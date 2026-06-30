package com.github.jozott00.wokwiintellij.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/**
 * Outbound message sent from the IntelliJ backend to the Wokwi iframe through the browser wrapper.
 *
 * The iframe also sends messages with a `command` field in the opposite direction. Keep this interface for
 * IDE-to-simulator messages; inbound protocol messages have their own typed model.
 */
interface OutboundMessage {
    val command: String

    /**
     * Wire-level command names expected by Wokwi's VS Code-compatible simulator endpoint.
     *
     * These names are part of the Wokwi browser protocol, so changing them changes the messages received by
     * `https://wokwi.com/vscode/wcode`.
     */
    object Command {
        /** Starts or restarts a simulation after Wokwi has sent its readiness `start` message. */
        const val START = "start"

        /** Replies to Wokwi's `loadResource` request with the requested binary/text resource. */
        const val RESOURCE_DATA = "resourceData"

        /** Forwards a remote GDB protocol packet from the IDE-hosted GDB server to Wokwi. */
        const val GDB_MESSAGE = "gdbMessage"

        /** Requests that Wokwi pause/break the running simulation, usually after a debugger connects. */
        const val GDB_BREAK = "gdbBreak"
    }

    /**
     * Starts or restarts Wokwi in simulator mode.
     *
     * This command is sent by the IDE after the embedded Wokwi iframe completes its wrapper handshake and sends its
     * readiness `start` message. It is also resent when firmware/configuration changes require a simulator restart,
     * or when Wokwi asks the IDE to switch to base64 payloads.
     */
    @Serializable
    data class SimulatorStart(
        override val command: String = Command.START,

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
    ) : OutboundMessage {
        /** Internal bridge metadata; not serialized into the Wokwi protocol payload. */
        @Transient
        val binaryEncoding: BinaryEncoding = BinaryEncoding.BASE64
    }

    /**
     * Response to Wokwi's `loadResource` request.
     *
     * Sent after Wokwi asks the IDE to load a simulator resource such as an ESP32 ROM file or a URL fallback. The
     * current IntelliJ bridge sends the resource buffer as base64 text.
     */
    @Serializable
    data class ResourceData(
        override val command: String = Command.RESOURCE_DATA,

        /** Requested resource bytes encoded for the active browser bridge. */
        val buffer: String,
    ) : OutboundMessage

    /**
     * Forwards one remote GDB protocol command from the IDE-hosted GDB server to Wokwi.
     *
     * Sent when a debugger connected to the local GDB server writes a validated packet. Wokwi replies with
     * `gdbResponse`, which the session forwards back to the active debugger socket.
     */
    @Serializable
    data class Gdb(
        override val command: String = Command.GDB_MESSAGE,

        /** Remote GDB protocol packet body without the `$...#checksum` framing. */
        val message: String,
    ) : OutboundMessage

    /**
     * Requests a debugger break/pause in Wokwi.
     *
     * Sent when a debugger connects to the local GDB server or sends an interrupt, so Wokwi stops execution and can
     * answer subsequent GDB protocol commands.
     */
    @Serializable
    data class GdbBreak(
        override val command: String = Command.GDB_BREAK,
    ) : OutboundMessage
}
