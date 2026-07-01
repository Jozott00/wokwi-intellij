package com.github.jozott00.wokwiintellij.core.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Encodes and decodes the Wokwi iframe protocol.
 *
 * The protocol is directional: IntelliJ encodes [OutboundMessage] instances for the iframe, and decodes raw iframe
 * JSON into [InboundMessage] instances. Unknown inbound commands are preserved as [InboundMessage.Unknown].
 */
object ProtocolCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val outboundJson = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private val inboundJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Serializes a typed IDE-to-Wokwi message into the JSON payload expected by the browser bridge.
     */
    fun encode(message: OutboundMessage): String =
        when (message) {
            is OutboundMessage.SimulatorStart -> {
                outboundJson.encodeToString(OutboundMessage.SimulatorStart.serializer(), message)
            }
            is OutboundMessage.ResourceData -> {
                outboundJson.encodeToString(OutboundMessage.ResourceData.serializer(), message)
            }
            is OutboundMessage.Gdb -> {
                outboundJson.encodeToString(OutboundMessage.Gdb.serializer(), message)
            }
            is OutboundMessage.GdbBreak -> {
                outboundJson.encodeToString(OutboundMessage.GdbBreak.serializer(), message)
            }
            else -> error("Unsupported outbound message: ${message::class.qualifiedName}")
        }

    /**
     * Decodes Wokwi-to-IDE JSON into typed inbound protocol models.
     *
     * The decoder is intentionally tolerant of unknown commands. It only treats malformed JSON, missing command
     * fields, or invalid known command payloads as malformed protocol input.
     */
    fun decode(data: String): InboundDecodeResult {
        val element = try {
            inboundJson.parseToJsonElement(data)
        } catch (e: SerializationException) {
            return InboundDecodeResult.Malformed(
                command = null,
                raw = data,
                reason = "Invalid JSON: ${e.message}",
            )
        }

        val payload = try {
            element.jsonObject
        } catch (_: IllegalArgumentException) {
            return InboundDecodeResult.Malformed(
                command = null,
                raw = data,
                reason = "Expected JSON object",
            )
        }

        if (payload.isEmpty()) {
            return InboundDecodeResult.Empty
        }

        val command = payload["command"]?.jsonPrimitive?.contentOrNull
            ?: return InboundDecodeResult.Malformed(
                command = null,
                raw = data,
                reason = "Missing command field",
            )

        return try {
            InboundDecodeResult.Decoded(decodeKnownOrUnknown(command, payload))
        } catch (e: SerializationException) {
            InboundDecodeResult.Malformed(
                command = command,
                raw = data,
                reason = e.message ?: "Invalid payload for command '$command'",
            )
        } catch (e: IllegalArgumentException) {
            InboundDecodeResult.Malformed(
                command = command,
                raw = data,
                reason = e.message ?: "Invalid payload for command '$command'",
            )
        }
    }

    private fun decodeKnownOrUnknown(command: String, payload: JsonObject): InboundMessage =
        when (command) {
            InboundMessage.Command.START -> inboundJson.decodeFromJsonElement<InboundMessage.Ready>(payload)
            InboundMessage.Command.SWITCH_TO_BASE64 -> {
                inboundJson.decodeFromJsonElement<InboundMessage.SwitchToBase64>(payload)
            }
            InboundMessage.Command.LOAD_RESOURCE -> {
                inboundJson.decodeFromJsonElement<InboundMessage.LoadResource>(payload)
            }
            InboundMessage.Command.UART_DATA -> inboundJson.decodeFromJsonElement<InboundMessage.UartData>(payload)
            InboundMessage.Command.CHIP_OUTPUT -> inboundJson.decodeFromJsonElement<InboundMessage.ChipOutput>(payload)
            InboundMessage.Command.WIFI_CONNECT -> {
                inboundJson.decodeFromJsonElement<InboundMessage.WifiConnect>(payload)
            }
            InboundMessage.Command.WIFI_FRAME -> inboundJson.decodeFromJsonElement<InboundMessage.WifiFrame>(payload)
            InboundMessage.Command.GDB_RESPONSE -> {
                inboundJson.decodeFromJsonElement<InboundMessage.GdbResponse>(payload)
            }
            else -> InboundMessage.Unknown(command, payload)
        }
}

sealed interface InboundDecodeResult {
    data object Empty : InboundDecodeResult

    data class Decoded(
        val message: InboundMessage,
    ) : InboundDecodeResult

    data class Malformed(
        val command: String?,
        val raw: String,
        val reason: String,
    ) : InboundDecodeResult
}
