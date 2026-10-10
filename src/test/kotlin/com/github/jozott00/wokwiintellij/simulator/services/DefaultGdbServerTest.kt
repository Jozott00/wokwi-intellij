package com.github.jozott00.wokwiintellij.simulator.services

import com.github.jozott00.wokwiintellij.core.ports.GdbEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun String.gdbChecksum(): String {
    val c = sumOf { it.code } and 0xff
    return "${(c ushr 4).toString(16)}${(c and 0xf).toString(16)}"
}

private fun String.gdbPacket(): String = "\$${this}#${gdbChecksum()}"

class DefaultGdbServerTest {

    @Test
    fun `client stays usable after collector resubscription`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val server = DefaultGdbServer(parentScope)
        val client = Socket()

        try {
            val port = server.listen(null).getOrThrow()
            client.connect(InetSocketAddress("127.0.0.1", port), 3000)
            client.soTimeout = 3000

            runBlocking<Unit> {
                val reader = BufferedReader(InputStreamReader(client.inputStream))
                val ack = withTimeout(3000) { reader.readLine() }
                assertNotNull(ack)
                assertTrue(ack.startsWith("+"))

                // Persistent collector
                val events = Channel<GdbEvent>(Channel.BUFFERED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    server.events.collect { events.send(it) }
                }

                // Receive Connected
                assertEquals(GdbEvent.Connected, withTimeout(3000) { events.receive() })

                // Cancel collector, launch replacement
                collector.cancel()
                collector.join()
                val collector2 = launch(start = CoroutineStart.UNDISPATCHED) {
                    server.events.collect { events.send(it) }
                }

                // Ctrl-C → Break
                client.outputStream.write(3)
                client.outputStream.flush()
                assertEquals(GdbEvent.Break, withTimeout(3000) { events.receive() })

                collector2.cancel()
                collector2.join()
            }
        } finally {
            client.close()
            server.close()
            parentJob.cancel()
        }
    }

    @Test
    fun `remote GDB packet is emitted as Message event`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val server = DefaultGdbServer(parentScope)
        val client = Socket()

        try {
            val port = server.listen(null).getOrThrow()
            client.connect(InetSocketAddress("127.0.0.1", port), 3000)
            client.soTimeout = 3000

            runBlocking<Unit> {
                val reader = BufferedReader(InputStreamReader(client.inputStream))
                withTimeout(3000) {
                    val ack = reader.readLine()
                    assertNotNull(ack)
                    assertTrue(ack.startsWith("+"))
                }

                val events = Channel<GdbEvent>(Channel.BUFFERED)
                val collector = parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    server.events.collect { events.send(it) }
                }

                // Skip Connected
                assertEquals(GdbEvent.Connected, withTimeout(3000) { events.receive() })

                // Send GDB packet
                val message = "qSupported"
                client.outputStream.write(message.gdbPacket().toByteArray())
                client.outputStream.flush()

                // ACK
                val ack2 = withTimeout(3000) { reader.readLine() }
                assertNotNull(ack2)
                assertTrue(ack2.startsWith("+"))

                // Message event
                val msg = withTimeout(3000) { events.receive() } as? GdbEvent.Message
                assertNotNull(msg)
                assertEquals(message, msg.message)

                collector.cancel()
                collector.join()
            }
        } finally {
            client.close()
            server.close()
            parentJob.cancel()
        }
    }

    @Test
    fun `sendResponse forwards response to connected client`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val server = DefaultGdbServer(parentScope)
        val client = Socket()

        try {
            val port = server.listen(null).getOrThrow()
            client.connect(InetSocketAddress("127.0.0.1", port), 3000)
            client.soTimeout = 3000

            runBlocking<Unit> {
                val reader = BufferedReader(InputStreamReader(client.inputStream))
                withTimeout(3000) {
                    val ack = reader.readLine()
                    assertNotNull(ack)
                    assertTrue(ack.startsWith("+"))
                }

                val events = Channel<GdbEvent>(Channel.BUFFERED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    server.events.collect { events.send(it) }
                }

                assertEquals(GdbEvent.Connected, withTimeout(3000) { events.receive() })

                // Establish active connection via a packet
                val message = "qSupported"
                client.outputStream.write(message.gdbPacket().toByteArray())
                client.outputStream.flush()
                val ack2 = withTimeout(3000) { reader.readLine() }
                assertNotNull(ack2)
                assertTrue(ack2.startsWith("+"))
                withTimeout(3000) { events.receive() } // consume Message

                // Server sends response
                val responseBody = "T05"
                server.sendResponse(responseBody.gdbPacket())

                val received = withTimeout(3000) { reader.readLine() }
                assertNotNull(received)
                assertEquals(responseBody.gdbPacket(), received)

                collector.cancel()
                collector.join()
            }
        } finally {
            client.close()
            server.close()
            parentJob.cancel()
        }
    }

    @Test
    fun `listen returns failure on occupied port`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val holder = ServerSocket(0)
        val occupiedPort = holder.localPort
        val server = DefaultGdbServer(parentScope)

        try {
            val result = server.listen(occupiedPort)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is java.net.BindException)
        } finally {
            server.close()
            holder.close()
            parentJob.cancel()
        }
    }

    @Test
    fun `close-before-listen rejects startup`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val server = DefaultGdbServer(parentScope)

        try {
            server.close()
            val result = server.listen(null)
            assertTrue(result.isFailure)
        } finally {
            parentJob.cancel()
        }
    }

    @Test
    fun `repeated listen does not replace original socket`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val server = DefaultGdbServer(parentScope)
        val client = Socket()

        try {
            val port1 = server.listen(null).getOrThrow()
            val result2 = server.listen(null)
            assertTrue(result2.isFailure)

            // Original port still works
            client.connect(InetSocketAddress("127.0.0.1", port1), 3000)
            client.soTimeout = 3000

            runBlocking<Unit> {
                val reader = BufferedReader(InputStreamReader(client.inputStream))
                val ack = withTimeout(3000) { reader.readLine() }
                assertNotNull(ack)
                assertTrue(ack.startsWith("+"))
            }
        } finally {
            client.close()
            server.close()
            parentJob.cancel()
        }
    }

    @Test
    fun `close releases sockets and cancels server jobs but leaves parent active`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val server = DefaultGdbServer(parentScope)
        val client = Socket()

        try {
            val port = server.listen(null).getOrThrow()
            client.connect(InetSocketAddress("127.0.0.1", port), 3000)
            client.soTimeout = 3000

            // Single BufferedReader for this client — used throughout
            val reader = BufferedReader(InputStreamReader(client.inputStream))

            runBlocking<Unit> {
                val ack = withTimeout(3000) { reader.readLine() }
                assertNotNull(ack)
                assertTrue(ack.startsWith("+"))

                // Wait for Connected to ensure connection is registered
                val events = Channel<GdbEvent>(Channel.BUFFERED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    server.events.collect { events.send(it) }
                }
                assertEquals(GdbEvent.Connected, withTimeout(3000) { events.receive() })
                collector.cancel()
                collector.join()
            }

            // Close server — keep client open
            server.close()
            server.close() // idempotent

            // Server-owned jobs must disappear; parent stays active
            runBlocking<Unit> {
                withTimeout(3000) {
                    parentJob.children.toList().forEach { it.join() }
                }
            }
            assertEquals(0, parentJob.children.count())
            assertTrue(parentJob.isActive)

            // Server port released
            val newSocket = ServerSocket(port)
            try {
                assertEquals(port, newSocket.localPort)
            } finally {
                newSocket.close()
            }

            // Client reader (same instance) sees EOF after server close
            val readResult = reader.read()
            assertEquals(-1, readResult)
        } finally {
            client.close()
            server.close()
            parentJob.cancel()
        }
    }

    @Test
    fun `parent cancellation closes remote client and stops server`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val server = DefaultGdbServer(parentScope)
        val client = Socket()

        try {
            val port = server.listen(null).getOrThrow()
            client.connect(InetSocketAddress("127.0.0.1", port), 3000)
            client.soTimeout = 3000

            val reader = BufferedReader(InputStreamReader(client.inputStream))

            runBlocking<Unit> {
                // Read initial '+' ack
                val ack = withTimeout(3000) { reader.readLine() }
                assertNotNull(ack)
                assertTrue(ack.startsWith("+"))

                // Collect events to ensure Connected is processed
                val events = Channel<GdbEvent>(Channel.BUFFERED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    server.events.collect { events.send(it) }
                }
                assertEquals(GdbEvent.Connected, withTimeout(3000) { events.receive() })
                collector.cancel()
                collector.join()
            }

            // Cancel parent while client is blocked reading
            parentJob.cancel()
            runBlocking<Unit> {
                withTimeout(3000) { parentJob.join() }
            }

            // Server must have stopped
            assertFalse(server.isRunning())

            // Client reader must see EOF (server closed the remote socket)
            val readResult = reader.read()
            assertEquals(-1, readResult)

            // Listener can rebind to original port
            val rebinding = ServerSocket(port)
            try {
                assertEquals(port, rebinding.localPort)
            } finally {
                rebinding.close()
            }
        } finally {
            client.close()
            server.close()
            parentJob.cancel()
        }
    }

    @Test
    fun `accept loop recovers after client disconnect and accepts second client`() {
        val parentJob = Job()
        val parentScope = CoroutineScope(Dispatchers.Default + parentJob)
        val server = DefaultGdbServer(parentScope)
        val client1 = Socket()
        val client2 = Socket()

        try {
            val port = server.listen(null).getOrThrow()

            // First client connects and reads initial '+'
            client1.connect(InetSocketAddress("127.0.0.1", port), 3000)
            client1.soTimeout = 3000
            val reader1 = BufferedReader(InputStreamReader(client1.inputStream))
            val ack1 = reader1.readLine()
            assertNotNull(ack1)
            assertTrue(ack1.startsWith("+"))

            // Close first client
            client1.close()

            // Second client connects to the same listener
            client2.connect(InetSocketAddress("127.0.0.1", port), 3000)
            client2.soTimeout = 3000
            val reader2 = BufferedReader(InputStreamReader(client2.inputStream))
            val ack2 = runBlocking { withTimeout(3000) { reader2.readLine() } }
            assertNotNull(ack2)
            assertTrue(ack2.startsWith("+"))

            // Server still running
            assertTrue(server.isRunning())
        } finally {
            client1.close()
            client2.close()
            server.close()
            parentJob.cancel()
        }
    }
}
