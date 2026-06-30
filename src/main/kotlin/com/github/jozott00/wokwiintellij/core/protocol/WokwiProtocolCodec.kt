package com.github.jozott00.wokwiintellij.core.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serializes typed IDE-to-Wokwi commands into the JSON payload expected by the browser bridge.
 *
 * Keeping this as the single encoding point makes the migration away from string-built commands incremental: legacy
 * callers can still ask for a JSON string while new session code can work with typed command objects.
 */
object WokwiProtocolCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(command: WokwiCommand): String =
        when (command) {
            is SimulatorStartPayload -> json.encodeToString(SimulatorStartPayload.serializer(), command)
            is ResourceDataPayload -> json.encodeToString(ResourceDataPayload.serializer(), command)
            is GdbMessagePayload -> json.encodeToString(GdbMessagePayload.serializer(), command)
            is GdbBreakPayload -> json.encodeToString(GdbBreakPayload.serializer(), command)
            else -> error("Unsupported Wokwi command: ${command::class.qualifiedName}")
        }
}

/**
 * Response to Wokwi's `loadResource` request.
 *
 * Sent after Wokwi asks the IDE to load a simulator resource such as an ESP32 ROM file or a URL fallback. The current
 * IntelliJ bridge sends the resource buffer as base64 text.
 */
@Serializable
data class ResourceDataPayload(
    override val command: String = WokwiCommandName.RESOURCE_DATA,

    /** Requested resource bytes encoded for the active browser bridge. */
    val buffer: String,
) : WokwiCommand

/**
 * Forwards one remote GDB protocol command from the IDE-hosted GDB server to Wokwi.
 *
 * Sent when a debugger connected to the local GDB server writes a validated packet. Wokwi replies with
 * `gdbResponse`, which the session forwards back to the active debugger socket.
 */
@Serializable
data class GdbMessagePayload(
    override val command: String = WokwiCommandName.GDB_MESSAGE,

    /** Remote GDB protocol packet body without the `$...#checksum` framing. */
    val message: String,
) : WokwiCommand

/**
 * Requests a debugger break/pause in Wokwi.
 *
 * Sent when a debugger connects to the local GDB server or sends an interrupt, so Wokwi stops execution and can answer
 * subsequent GDB protocol commands.
 */
@Serializable
data class GdbBreakPayload(
    override val command: String = WokwiCommandName.GDB_BREAK,
) : WokwiCommand
