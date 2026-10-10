package com.github.jozott00.wokwiintellij.simulator.services

import com.github.jozott00.wokwiintellij.core.ports.GdbEvent
import com.github.jozott00.wokwiintellij.core.ports.GdbServer
import com.github.jozott00.wokwiintellij.utils.runCloseable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Default socket-backed implementation of the session-facing [GdbServer] port.
 *
 * The server listens for local debugger TCP connections, turns debugger-side remote GDB protocol activity into
 * [GdbEvent] values, and writes Wokwi responses back to the active debugger connection. Wokwi protocol forwarding
 * remains owned by `core.session.WokwiSession`.
 *
 * The server owns a child [SupervisorJob] derived from [parentScope], so [close] cancels only its own work
 * without cancelling the supplied parent scope.
 *
 * @param parentScope the parent coroutine scope; the server creates its own child scope for all jobs.
 */
class DefaultGdbServer(parentScope: CoroutineScope) : GdbServer, Closeable {

    private val stateLock = Any()
    private var closed = false
    private var serverSocket: ServerSocket? = null
    private var currentConnection: GdbClientConnection? = null
    private val eventChannel = Channel<GdbEvent>(Channel.BUFFERED)
    override val events: Flow<GdbEvent> = eventChannel.receiveAsFlow()
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)

    init {
        // Cancellation of the supplied scope also completes the server's socket/channel lifetime.
        job.invokeOnCompletion { close() }
    }

    /** Binds synchronously. A closed or already listening server rejects another bind. */
    fun listen(port: Int?): Result<Int> {
        val socket = try {
            ServerSocket(port ?: 0)
        } catch (error: Exception) {
            return Result.failure(error)
        }
        val boundPort = socket.localPort
        val failure = synchronized(stateLock) {
            when {
                closed || !job.isActive -> IllegalStateException("GDB server is closed")
                serverSocket?.isClosed == false -> IllegalStateException("GDB server is already listening")
                else -> { serverSocket = socket; null }
            }
        }
        if (failure != null) {
            closeQuietly(socket)
            return Result.failure(failure)
        }
        LOG.info("GDB server listening on port $boundPort")
        scope.launch(Dispatchers.IO) { acceptConnections(socket) }
        return Result.success(boundPort)
    }

    private suspend fun acceptConnections(socket: ServerSocket) {
        try {
            while (currentCoroutineContext().isActive) {
                val client = socket.runCloseable { it.accept() }
                handleConnection(client)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (job.isActive && !socket.isClosed) {
                LOG.log(Level.WARNING, "GDB accept failed", error)
                eventChannel.trySend(GdbEvent.Error("GDB server error", error.message ?: "Accept failed", error))
            }
        } finally {
            synchronized(stateLock) { if (serverSocket === socket) serverSocket = null }
            closeQuietly(socket)
        }
    }

    private suspend fun handleConnection(socket: Socket) = socket.use {
        try {
            val connection = GdbClientConnection(socket, eventChannel)
            val published = synchronized(stateLock) {
                if (closed || !job.isActive) false else {
                    currentConnection = connection
                    true
                }
            }
            if (!published) return@use
            try {
                connection.process()
            } finally {
                synchronized(stateLock) { if (currentConnection === connection) currentConnection = null }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            // A disconnected/reset debugger does not terminate the listener.
            currentCoroutineContext().ensureActive()
            LOG.log(Level.FINE, "GDB client disconnected", error)
        }
    }

    fun getCurrentServerPort(): Int? = synchronized(stateLock) {
        serverSocket?.takeUnless { it.isClosed }?.localPort
    }

    fun isRunning(): Boolean = getCurrentServerPort() != null

    override fun sendResponse(response: String) {
        val connection = synchronized(stateLock) { currentConnection } ?: return
        scope.launch(Dispatchers.IO) { connection.writeResponse(response) }
    }

    /** Closes owned sockets/jobs and the stable event stream, leaving the parent scope active. */
    override fun close() {
        val owned = synchronized(stateLock) {
            if (closed) return
            closed = true
            (currentConnection to serverSocket).also {
                currentConnection = null
                serverSocket = null
            }
        }
        job.cancel()
        closeQuietly(owned.first)
        closeQuietly(owned.second)
        eventChannel.close()
    }

    private fun closeQuietly(closeable: Closeable?) {
        try { closeable?.close() } catch (_: IOException) { /* Already disconnected. */ }
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
    suspend fun process() {
        writer.println("+")
        dispatchEvent(GdbEvent.Connected)

        var buf = ""
        while (true) {
            val data: Int
            try {
                data = socket.runCloseable { reader.read() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                return
            }
            if (data == -1) break
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
                    writer.println('-')
                    LOG.warning("GDB checksum error in message: $message")
                } else {
                    writer.println('+')

                    if (checkDetach(message))
                        return

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

    private suspend fun dispatchEvent(event: GdbEvent) {
        try {
            eventChannel.send(event)
        } catch (_: kotlinx.coroutines.channels.ClosedSendChannelException) {
            // channel closed during shutdown, ignore
        }
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
            writer.println("+$#00")
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
