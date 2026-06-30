package com.github.jozott00.wokwiintellij.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/**
 * Starts or restarts Wokwi in simulator mode.
 *
 * This command is sent by the IDE after the embedded Wokwi iframe completes its wrapper handshake and sends its
 * readiness `start` message. It is also resent when firmware/configuration changes require a simulator restart, or
 * when Wokwi asks the IDE to switch to base64 payloads.
 */
@Serializable
data class SimulatorStartPayload(
    override val command: String = WokwiCommandName.START,

    /** Raw `diagram.json` content describing the simulated board, parts, and connections. */
    val diagram: String,

    /** Wokwi license string passed through to the simulator runtime. */
    val license: String,

    /** Firmware payload. Current IntelliJ bridge sends this as base64 text. */
    val firmware: String,

    /** Firmware format understood by Wokwi, for example `bin`, `hex`, or `uf2`. */
    val firmwareFormat: String,

    /** Tells Wokwi that `firmware` is base64 encoded instead of a transferred binary object. */
    val firmwareB64: Boolean = true,

    /** Starts the simulator paused so a debugger can attach before execution continues. */
    val pause: Boolean = false,

    /** Enables IDE-mediated WiFi gateway support. Disabled until the IntelliJ gateway bridge exists. */
    val useGateway: Boolean = false,

    /** Hides Wokwi's built-in serial monitor because the IDE provides its own console/terminal. */
    val disableSerialMonitor: Boolean = true,

    /** Local GDB server port to expose to Wokwi when debugging is enabled. */
    val gdbPort: Int? = null,

    /** Custom chip definitions to load before the simulation starts. */
    val chips: List<JsonObject>? = null,

    /** Requests that Wokwi hide personal information in the simulator UI. */
    val hidePersonalInfo: Boolean? = null,
) : WokwiCommand {
    /** Internal bridge metadata; not serialized into the Wokwi protocol payload. */
    @Transient
    val binaryEncoding: BinaryEncoding = BinaryEncoding.BASE64
}
