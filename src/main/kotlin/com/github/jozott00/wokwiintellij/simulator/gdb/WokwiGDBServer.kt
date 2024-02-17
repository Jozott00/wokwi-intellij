package com.github.jozott00.wokwiintellij.simulator.gdb

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

sealed class GDBServerEvent {
    data object Connected : GDBServerEvent()
    data class Error(val error: Throwable) : GDBServerEvent()
    data class Message(val message: String) : GDBServerEvent()
    data object Break : GDBServerEvent()
}

class WokwiGDBServer : Disposable {

    private var socket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private val eventChannel = Channel<GDBServerEvent> { Channel.BUFFERED }
    val events = eventChannel.receiveAsFlow()

    suspend fun listen(port: Int) {
        withContext(Dispatchers.IO) {
            socket = ServerSocket(port, 1)

            LOG.info("GDB Server listening on port $port")

            while (true) {
                val clientSocket = try {
                    socket?.accept() ?: break
                } catch (e: Exception) {
                    break
                }

                if (activeSocket != null) {
                    clientSocket.close()
                }

                LOG.info("Client connected.")
                handleConnection(clientSocket)
            }
        }
    }

    private suspend fun handleConnection(socket: Socket) {
        activeSocket = socket
        val processor = MessageProcessor(socket, eventChannel)
        processor.process()
        activeSocket = null
    }

    fun sendResponse(response: String) {
        this.activeSocket?.let {
            PrintWriter(it.getOutputStream(), true).println(response)
        }
    }

    override fun dispose() {
        activeSocket?.close()
        socket?.close()
        eventChannel.close()
    }


    companion object {
        val LOG = logger<WokwiGDBServer>()
    }

}

private class MessageProcessor(private val socket: Socket, private val eventChannel: Channel<GDBServerEvent>) {

    suspend fun process() = socket.use {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)
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
                    LOG.debug("Received: $message")
                    dispatchEvent(GDBServerEvent.Message(message))
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun dispatchEvent(event: GDBServerEvent) = GlobalScope.launch(Dispatchers.IO) {
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


    companion object {
        val LOG = logger<WokwiGDBServer>()
    }
}