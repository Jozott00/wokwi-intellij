package com.github.jozott00.wokwiintellij.core.session

import com.github.jozott00.wokwiintellij.core.ports.WokwiTransport
import com.github.jozott00.wokwiintellij.core.protocol.InboundDecodeResult
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WokwiSessionTest {

    @Test
    fun `start waits for browser readiness before sending simulator payload`() {
        val transport = FakeTransport()
        val listener = RecordingListener()
        val session = createSession(transport, listener)

        session.start()
        assertEquals(emptyList(), transport.sentMessages)

        assertTrue(transport.receive("""{"command":"start"}"""))

        val payload = Json.parseToJsonElement(transport.sentMessages.single()).jsonObject
        assertEquals("start", payload["command"]?.jsonPrimitive?.contentOrNull)
        assertEquals("""{"version":1}""", payload["diagram"]?.jsonPrimitive?.contentOrNull)
        assertEquals("AQID", payload["firmware"]?.jsonPrimitive?.contentOrNull)
        assertEquals("bin", payload["firmwareFormat"]?.jsonPrimitive?.contentOrNull)
        assertEquals("license-key", payload["license"]?.jsonPrimitive?.contentOrNull)
        assertTrue(payload["pause"]!!.jsonPrimitive.boolean)
        assertEquals(1, listener.startedConfigs.size)
    }

    @Test
    fun `browser readiness before start is remembered`() {
        val transport = FakeTransport()
        val session = createSession(transport)

        assertTrue(transport.receive("""{"command":"start"}"""))
        assertEquals(emptyList(), transport.sentMessages)

        session.start()

        assertEquals(1, transport.sentMessages.size)
    }

    @Test
    fun `uses updated start config when restarting`() {
        val transport = FakeTransport()
        val session = createSession(transport)
        transport.receive("""{"command":"start"}""")

        session.updateStartConfig(
            WokwiSessionStartConfig(
                license = "updated-license",
                diagram = """{"updated":true}""",
                firmware = byteArrayOf(4, 5),
                firmwareFormat = "hex",
                waitForDebugger = false,
            )
        )
        session.start()

        val payload = Json.parseToJsonElement(transport.sentMessages.single()).jsonObject
        assertEquals("updated-license", payload["license"]?.jsonPrimitive?.contentOrNull)
        assertEquals("""{"updated":true}""", payload["diagram"]?.jsonPrimitive?.contentOrNull)
        assertEquals("BAU=", payload["firmware"]?.jsonPrimitive?.contentOrNull)
        assertEquals("hex", payload["firmwareFormat"]?.jsonPrimitive?.contentOrNull)
        assertFalse(payload["pause"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `load resource sends resource data and reports running once`() {
        val transport = FakeTransport()
        val listener = RecordingListener()
        val requestedUrls = mutableListOf<String>()
        createSession(
            transport = transport,
            listener = listener,
            resourceLoader = { message ->
                requestedUrls.add(message.url)
                "hello".encodeToByteArray()
            },
        )

        assertTrue(transport.receive("""{"command":"loadResource","url":"https://example.com/rom.bin"}"""))
        assertTrue(transport.receive("""{"command":"loadResource","url":"https://example.com/rom.bin"}"""))

        assertEquals(
            listOf("https://example.com/rom.bin", "https://example.com/rom.bin"),
            requestedUrls,
        )
        val firstPayload = Json.parseToJsonElement(transport.sentMessages.first()).jsonObject
        assertEquals("resourceData", firstPayload["command"]?.jsonPrimitive?.contentOrNull)
        assertEquals("aGVsbG8=", firstPayload["buffer"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1, listener.runningCount)
    }

    @Test
    fun `forwards uart and gdb traffic`() {
        val transport = FakeTransport()
        val listener = RecordingListener()
        val session = createSession(
            transport = transport,
            listener = listener,
        )

        session.sendGdbMessage("qSupported")
        session.sendGdbBreak()
        assertTrue(transport.receive("""{"command":"gdbResponse","response":"OK"}"""))
        assertTrue(transport.receive("""{"command":"uartData","bytes":[65,66,10]}"""))

        val gdbMessage = Json.parseToJsonElement(transport.sentMessages[0]).jsonObject
        val gdbBreak = Json.parseToJsonElement(transport.sentMessages[1]).jsonObject
        assertEquals("gdbMessage", gdbMessage["command"]?.jsonPrimitive?.contentOrNull)
        assertEquals("qSupported", gdbMessage["message"]?.jsonPrimitive?.contentOrNull)
        assertEquals("gdbBreak", gdbBreak["command"]?.jsonPrimitive?.contentOrNull)
        assertEquals(listOf("OK"), listener.gdbResponses)
        assertContentEquals("AB\n".encodeToByteArray(), listener.uartBytes.single())
    }

    @Test
    fun `malformed and unknown messages are rejected and reported`() {
        val transport = FakeTransport()
        val listener = RecordingListener()
        createSession(transport, listener)

        assertFalse(transport.receive("{"))
        assertFalse(transport.receive("""{"command":"futureCommand","payload":true}"""))

        assertEquals(1, listener.malformedMessages.size)
        assertEquals(null, listener.malformedMessages.single().command)
        assertEquals(listOf("futureCommand"), listener.unknownMessages.map { it.command })
    }

    @Test
    fun `dispose removes transport listener`() {
        val transport = FakeTransport()
        val session = createSession(transport)

        assertEquals(1, transport.listenerCount)
        session.dispose()

        assertEquals(0, transport.listenerCount)
    }

    private fun createSession(
        transport: FakeTransport,
        listener: RecordingListener = RecordingListener(),
        resourceLoader: WokwiSession.ResourceLoader = WokwiSession.ResourceLoader { ByteArray(0) },
    ) = WokwiSession(
        transport = transport,
        initialConfig = WokwiSessionStartConfig(
            license = "license-key",
            diagram = """{"version":1}""",
            firmware = byteArrayOf(1, 2, 3),
            firmwareFormat = "bin",
            waitForDebugger = true,
        ),
        resourceLoader = resourceLoader,
        listener = listener,
    )

    private class RecordingListener : WokwiSession.Listener {
        val startedConfigs = mutableListOf<WokwiSessionStartConfig>()
        var runningCount = 0
        val uartBytes = mutableListOf<ByteArray>()
        val gdbResponses = mutableListOf<String>()
        val malformedMessages = mutableListOf<InboundDecodeResult.Malformed>()
        val unknownMessages = mutableListOf<InboundMessage.Unknown>()

        override fun onStarted(config: WokwiSessionStartConfig) {
            startedConfigs.add(config)
        }

        override fun onRunning() {
            runningCount++
        }

        override fun onUartData(bytes: ByteArray) {
            uartBytes.add(bytes)
        }

        override fun onGdbResponse(response: String) {
            gdbResponses.add(response)
        }

        override fun onMalformedMessage(message: InboundDecodeResult.Malformed) {
            malformedMessages.add(message)
        }

        override fun onUnknownMessage(message: InboundMessage.Unknown) {
            unknownMessages.add(message)
        }
    }

    private class FakeTransport : WokwiTransport {
        val sentMessages = mutableListOf<String>()
        private val listeners = mutableListOf<WokwiTransport.Listener>()

        val listenerCount: Int
            get() = listeners.size

        override fun send(message: String) {
            sentMessages.add(message)
        }

        override fun subscribe(listener: WokwiTransport.Listener) {
            listeners.add(listener)
        }

        override fun removeSubscriber(listener: WokwiTransport.Listener) {
            listeners.remove(listener)
        }

        override fun dispose() {
            listeners.clear()
        }

        fun receive(message: String): Boolean {
            return listeners.all { it.messageReceived(message) }
        }
    }
}
