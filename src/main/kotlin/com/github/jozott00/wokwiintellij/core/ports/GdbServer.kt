package com.github.jozott00.wokwiintellij.core.ports

import kotlinx.coroutines.flow.Flow

/**
 * Session-facing remote GDB server port.
 *
 * Implementations own socket, IDE, and lifecycle details. The core session only reacts to debugger events and forwards
 * Wokwi responses back to the active debugger connection.
 */
interface GdbServer {
    /**
     * Stream of debugger-side events that the Wokwi session should translate into Wokwi protocol messages.
     */
    val events: Flow<GdbEvent>

    /**
     * Writes one Wokwi-generated remote GDB protocol response back to the currently connected debugger client.
     */
    fun sendResponse(response: String)
}

/**
 * Events emitted by the local GDB server adapter.
 */
sealed interface GdbEvent {
    /**
     * A debugger client connected to the local server.
     */
    data object Connected : GdbEvent

    /**
     * The local server failed or encountered an infrastructure-level error.
     */
    data class Error(val error: Throwable) : GdbEvent

    /**
     * A validated remote GDB protocol packet body from the debugger, without `$...#checksum` framing.
     */
    data class Message(val message: String) : GdbEvent

    /**
     * The debugger requested an interrupt, usually via Ctrl-C or immediately after connecting.
     */
    data object Break : GdbEvent
}
