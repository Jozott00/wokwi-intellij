package com.github.jozott00.wokwiintellij.simulator.gdb

import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.github.jozott00.wokwiintellij.utils.runCloseable
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
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


sealed class GDBServerEvent {
    data object Connected : GDBServerEvent()
    data class Error(val error: Throwable) : GDBServerEvent()
    data class Message(val message: String) : GDBServerEvent()
    data object Break : GDBServerEvent()
}

interface GDBServerCommunicator {
    fun getMessageFlow(): Flow<GDBServerEvent>
    fun sendResponse(response: String)
}


class WokwiGDBServer(private val cs: CoroutineScope, parentDisposable: Disposable) : GDBServerCommunicator, Disposable {

    init {
        Disposer.register(parentDisposable, this)
    }

    private var serverSocket: ServerSocket? = null
    private var currentMessageProcessor: MessageProcessor? = null
    private var eventChannel = Channel<GDBServerEvent> { Channel.BUFFERED }

    fun listen(port: Int) = cs.launch(Dispatchers.IO) {
        try {
            ServerSocket(port).use { socket ->
                serverSocket = socket

                LOG.info("GDB Server listening on port $port")

                while (true) {
                    val clientSocket = try {
                        socket.runCloseable { it.accept() }
                    } catch (e: SocketException) {
                        break
                    }
                    currentMessageProcessor?.close()
                    currentMessageProcessor = null
                    handleConnection(clientSocket)
                }
            }
        } catch (e: Exception) {
            LOG.warn(e)
            WokwiNotifier.notifyBalloonAsync(
                "Couldn't start GDB server",
                "Failed to create server socket: ${e.message}",
                NotificationType.ERROR
            )
        }
    }

    fun isRunning() = serverSocket?.isClosed?.not() ?: false

    private suspend fun handleConnection(socket: Socket) {
        currentMessageProcessor = MessageProcessor(socket, eventChannel)
        currentMessageProcessor?.process()
    }

    override fun sendResponse(response: String) = cs.launch(Dispatchers.IO) {
        currentMessageProcessor?.writeResponse(response)
    }.let { }

    override fun dispose() {
        currentMessageProcessor?.close()
        currentMessageProcessor = null

        serverSocket?.close()
        serverSocket = null
    }


    override fun getMessageFlow(): Flow<GDBServerEvent> {
        return eventChannel.receiveAsFlow()
    }

    fun resetEventChannel() {
        eventChannel.close()
        eventChannel = Channel { Channel.BUFFERED }
    }

    companion object {
        val LOG = logger<WokwiGDBServer>()
    }
}

private class MessageProcessor(private val socket: Socket, private val eventChannel: Channel<GDBServerEvent>) :
    Closeable {

    private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val writer = PrintWriter(socket.getOutputStream(), true)

    suspend fun process() = socket.use {
        writer.println("+")

        dispatchEvent(GDBServerEvent.Connected)

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
                LOG.debug("Received break")
                dispatchEvent(GDBServerEvent.Break)
                continue
            }
            buf += data.toChar()
            while (shouldContinueProcessingMessage(buf)) {
                val message = extractMessage(buf)
                val receivedChecksum = extractChecksum(buf)
                buf = trimProcessedParts(buf)

                if (calculateChecksum(message) != receivedChecksum) {
                    writer.println('-') // Negative acknowledgment
                    LOG.warn("Warning: GDB checksum error in message: $message")
                } else {
                    writer.println('+') // Positive acknowledgment

                    if (checkDetach(message))
                        return@use

                    dispatchEvent(GDBServerEvent.Message(message))
                }
            }
        }
    }

    fun writeResponse(response: String) {
        writer.println(response)
    }

    private suspend fun dispatchEvent(event: GDBServerEvent) = withContext(Dispatchers.IO) {
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
        val LOG = logger<WokwiGDBServer>()
    }

    override fun close() {
        if (!socket.isClosed)
            socket.close()
    }
}