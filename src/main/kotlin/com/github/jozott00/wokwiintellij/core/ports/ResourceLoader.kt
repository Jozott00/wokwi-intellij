package com.github.jozott00.wokwiintellij.core.ports

import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage

/**
 * Loads resources requested by Wokwi while starting or running the simulation.
 */
fun interface ResourceLoader {
    /**
     * Returns raw bytes for [message]. The session handles transport encoding before replying to Wokwi.
     */
    fun load(message: InboundMessage.LoadResource): ByteArray
}
