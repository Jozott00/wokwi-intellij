package com.github.jozott00.wokwiintellij.core.ports

import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage

/**
 * Loads resources requested by Wokwi while starting or running the simulation.
 */
fun interface ResourceLoader {
    /**
     * Returns raw bytes for [message]. Implementations move blocking I/O off the caller's dispatcher and bound waits.
     * The session owns cancellation, request ordering, and transport encoding before replying to Wokwi.
     */
    suspend fun load(message: InboundMessage.LoadResource): ByteArray
}
