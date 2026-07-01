package com.github.jozott00.wokwiintellij.simulator.services

import com.github.jozott00.wokwiintellij.core.ports.GdbEvent
import com.github.jozott00.wokwiintellij.core.ports.GdbServer
import com.github.jozott00.wokwiintellij.utils.runCloseable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Default socket-backed implementation of the session-facing [GdbServer] port.
 *
 * The server listens for local debugger TCP connections, turns debugger-side remote GDB protocol activity into
 * [GdbEvent] values, and writes Wokwi responses back to the active debugger connection. Wokwi protocol forwarding
 * remains owned by `core.session.WokwiSession`.
 *
 * @param cs coroutine scope used for socket accept/read/write work.
 */
class DefaultGdbServer(private val cs: CoroutineScope) : GdbServer, Closeable {
    private var serverSocket: ServerSocket? = null
    private var currentConnection: GdbClientConnection? = null
    private var eventChannel = Channel<GdbEvent>(Channel.BUFFERED)

    override val events: Flow<GdbEvent>
        get() = eventChannel.receiveAsFlow()

    /**
     * Listens for incoming connections on the specified port and handles them.
     *
     * @param port The port number to listen on. If null, a random port will be used.
     */
    fun listen(port: Int?) {
        val socket = try {
            ServerSocket(port ?: 0)
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "Failed to create GDB server socket", e)
            eventChannel.trySend(
                GdbEvent.Error(
                    title = "Couldn't start GDB server",
                    message = "Failed to create server socket: ${e.message}",
                    cause = e,
                )
            )
            return
        }

        serverSocket = socket
        LOG.info("GDB server listening on port ${socket.localPort}")

        cs.launch(Dispatchers.IO) {
            acceptConnections(socket)
        }
    }

    private suspend fun acceptConnections(socket: ServerSocket) = socket.use {
        while (true) {
            val clientSocket = try {
                socket.runCloseable { it.accept() }
            } catch (e: SocketException) {
                break
            }
            currentConnection?.close()
            currentConnection = null
            handleConnection(clientSocket)
        }
    }

    /**
     * Returns the bound local TCP port after [listen] succeeds.
     */
    fun getCurrentServerPort() = serverSocket?.localPort

    /**
     * Returns whether the local server socket is currently open.
     */
    fun isRunning() = serverSocket?.isClosed?.not() ?: false

    private suspend fun handleConnection(socket: Socket) {
        currentConnection = GdbClientConnection(socket, eventChannel)
        currentConnection?.process()
    }

    override fun sendResponse(response: String) = cs.launch(Dispatchers.IO) {
        currentConnection?.writeResponse(response)
    }.let { }

    /**
     * Closes the active debugger connection and server socket.
     */
    override fun close() {
        currentConnection?.close()
        currentConnection = null

        serverSocket?.close()
        serverSocket = null
    }

    /**
     * Replaces the event channel when reusing a running server for a new simulator session.
     */
    fun resetEventChannel() {
        eventChannel.close()
        eventChannel = Channel(Channel.BUFFERED)
    }

    companion object {
        private val LOG: Logger = Logger.getLogger(DefaultGdbServer::class.java.name)
    }
}

/**
 * Handles one debugger TCP connection using the remote GDB protocol framing.
 *
 * This class validates `$message#checksum` packets, emits packet bodies as [GdbEvent.Message], emits
 * [GdbEvent.Break] for Ctrl-C, and writes Wokwi response packets back to the debugger socket.
 */
private class GdbClientConnection(private val socket: Socket, private val eventChannel: Channel<GdbEvent>) :
    Closeable {

    private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val writer = PrintWriter(socket.getOutputStream(), true)

    /**
     * Reads debugger input until the socket closes, emitting validated GDB protocol events.
     */
    suspend fun process() = socket.use {
        writer.println("+")

        dispatchEvent(GdbEvent.Connected)

        var buf = ""
        while (true) {
            val data = try {
                reader.read()
            } catch (e: Exception) {
                return@use
            }
            if (data == -1)
                break
            if (data == 3) {
                LOG.fine("Received break")
                dispatchEvent(GdbEvent.Break)
                continue
            }
            buf += data.toChar()
            while (shouldContinueProcessingMessage(buf)) {
                val message = extractMessage(buf)
                val receivedChecksum = extractChecksum(buf)
                buf = trimProcessedParts(buf)

                if (calculateChecksum(message) != receivedChecksum) {
                    writer.println('-') // Negative acknowledgment
                    LOG.warning("GDB checksum error in message: $message")
                } else {
                    writer.println('+') // Positive acknowledgment

                    if (checkDetach(message))
                        return@use

                    dispatchEvent(GdbEvent.Message(message))
                }
            }
        }
    }

    /**
     * Writes a remote GDB protocol response received from Wokwi to the debugger socket.
     */
    fun writeResponse(response: String) {
        writer.println(response)
    }

    private suspend fun dispatchEvent(event: GdbEvent) = withContext(Dispatchers.IO) {
        eventChannel.send(event)
    }

    private fun shouldContinueProcessingMessage(buf: String): Boolean {
        val dollar = buf.indexOf('$')
        val hash = buf.indexOf('#')
        return dollar > -1 && hash > -1 && hash > dollar && hash + 3 <= buf.length
    }

    private fun extractMessage(buf: String): String {
        val dollar = buf.indexOf('$')
        val hash = buf.indexOf('#')
        return buf.substring(dollar + 1, hash)
    }

    private fun extractChecksum(buf: String): String {
        val hash = buf.indexOf('#')
        return buf.substring(hash + 1, hash + 3)
    }

    private fun trimProcessedParts(buf: String): String {
        val hash = buf.indexOf('#')
        return buf.substring(hash + 3)
    }

    private fun calculateChecksum(message: String): String {
        val checksum = message.sumOf { it.code } and 0xff
        return "${(checksum ushr 4).toString(16)}${(checksum and 0xf).toString(16)}"
    }

    private fun checkDetach(message: String): Boolean {
        if (message == "D") {
            writer.println("+\$#00")
            return true
        }
        return false
    }

    companion object {
        private val LOG: Logger = Logger.getLogger(GdbClientConnection::class.java.name)
    }

    /**
     * Closes the debugger socket if it is still open.
     */
    override fun close() {
        if (!socket.isClosed)
            socket.close()
    }
}
