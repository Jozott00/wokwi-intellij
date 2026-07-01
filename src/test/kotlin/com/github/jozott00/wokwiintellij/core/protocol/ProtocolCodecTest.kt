package com.github.jozott00.wokwiintellij.core.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolCodecTest {

    @Test
    fun `encodes simulator start payload`() {
        val encoded = ProtocolCodec.encode(
            OutboundMessage.SimulatorStart(
                diagram = """{"version":1}""",
                license = "license-key",
                firmware = "AQID",
                firmwareFormat = "bin",
                pause = true,
                gdbPort = 3333,
                hidePersonalInfo = true,
            )
        )

        val payload = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("start", payload["command"]?.jsonPrimitive?.contentOrNull)
        assertEquals("""{"version":1}""", payload["diagram"]?.jsonPrimitive?.contentOrNull)
        assertEquals("license-key", payload["license"]?.jsonPrimitive?.contentOrNull)
        assertEquals("AQID", payload["firmware"]?.jsonPrimitive?.contentOrNull)
        assertEquals("bin", payload["firmwareFormat"]?.jsonPrimitive?.contentOrNull)
        assertTrue(payload["firmwareB64"]!!.jsonPrimitive.boolean)
        assertTrue(payload["pause"]!!.jsonPrimitive.boolean)
        assertFalse(payload["useGateway"]!!.jsonPrimitive.boolean)
        assertTrue(payload["disableSerialMonitor"]!!.jsonPrimitive.boolean)
        assertEquals(3333, payload["gdbPort"]!!.jsonPrimitive.int)
        assertTrue(payload["hidePersonalInfo"]!!.jsonPrimitive.boolean)
        assertNull(payload["chips"])
    }

    @Test
    fun `encodes resource and gdb payloads`() {
        val resource = Json.parseToJsonElement(
            ProtocolCodec.encode(OutboundMessage.ResourceData(buffer = "aGVsbG8="))
        ).jsonObject
        val gdb = Json.parseToJsonElement(
            ProtocolCodec.encode(OutboundMessage.Gdb(message = "qSupported"))
        ).jsonObject
        val gdbBreak = Json.parseToJsonElement(
            ProtocolCodec.encode(OutboundMessage.GdbBreak())
        ).jsonObject

        assertEquals("resourceData", resource["command"]?.jsonPrimitive?.contentOrNull)
        assertEquals("aGVsbG8=", resource["buffer"]?.jsonPrimitive?.contentOrNull)
        assertEquals("gdbMessage", gdb["command"]?.jsonPrimitive?.contentOrNull)
        assertEquals("qSupported", gdb["message"]?.jsonPrimitive?.contentOrNull)
        assertEquals("gdbBreak", gdbBreak["command"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `decodes known inbound messages`() {
        val ready = ProtocolCodec.decode("""{"command":"start"}""")
        val switchToBase64 = ProtocolCodec.decode("""{"command":"switchToBase64"}""")
        val uart = ProtocolCodec.decode("""{"command":"uartData","bytes":[65,66,10]}""")

        assertIs<InboundDecodeResult.Decoded>(ready)
        assertIs<InboundMessage.Ready>(ready.message)

        assertIs<InboundDecodeResult.Decoded>(switchToBase64)
        assertIs<InboundMessage.SwitchToBase64>(switchToBase64.message)

        assertIs<InboundDecodeResult.Decoded>(uart)
        val uartMessage = assertIs<InboundMessage.UartData>(uart.message)
        assertEquals("AB\n", String(uartMessage.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun `preserves unknown inbound payloads`() {
        val result = ProtocolCodec.decode("""{"command":"newCommand","extra":"value"}""")

        assertIs<InboundDecodeResult.Decoded>(result)
        val message = assertIs<InboundMessage.Unknown>(result.message)
        assertEquals("newCommand", message.command)
        assertEquals("value", message.raw["extra"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `reports empty and malformed inbound payloads`() {
        assertIs<InboundDecodeResult.Empty>(ProtocolCodec.decode("{}"))

        val invalidJson = ProtocolCodec.decode("{")
        assertIs<InboundDecodeResult.Malformed>(invalidJson)
        assertNull(invalidJson.command)

        val missingCommand = ProtocolCodec.decode("""{"bytes":[1]}""")
        assertIs<InboundDecodeResult.Malformed>(missingCommand)
        assertEquals("Missing command field", missingCommand.reason)

        val malformedKnownCommand = ProtocolCodec.decode("""{"command":"uartData","bytes":"bad"}""")
        assertIs<InboundDecodeResult.Malformed>(malformedKnownCommand)
        assertEquals("uartData", malformedKnownCommand.command)
    }
}
