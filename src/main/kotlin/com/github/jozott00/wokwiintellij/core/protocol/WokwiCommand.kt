package com.github.jozott00.wokwiintellij.core.protocol

/**
 * Outbound command sent from the IntelliJ backend to the Wokwi iframe through the browser wrapper.
 *
 * Wokwi also sends messages with a `command` field in the opposite direction. Keep this interface for
 * IDE-to-Wokwi payloads; incoming protocol messages should get their own typed model as the migration continues.
 */
interface WokwiCommand {
    val command: String
}

/**
 * Wire-level command names expected by Wokwi's VS Code-compatible simulator endpoint.
 *
 * These names are part of the Wokwi browser protocol, so changing them changes the messages received by
 * `https://wokwi.com/vscode/wcode`.
 */
object WokwiCommandName {
    /** Starts or restarts a simulation after Wokwi has sent its readiness `start` message. */
    const val START = "start"

    /** Replies to Wokwi's `loadResource` request with the requested binary/text resource. */
    const val RESOURCE_DATA = "resourceData"

    /** Forwards a remote GDB protocol packet from the IDE-hosted GDB server to Wokwi. */
    const val GDB_MESSAGE = "gdbMessage"

    /** Requests that Wokwi pause/break the running simulation, usually after a debugger connects. */
    const val GDB_BREAK = "gdbBreak"
}
