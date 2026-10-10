package com.github.jozott00.wokwiintellij.core.ports

/**
 * Session-facing transport for raw Wokwi protocol messages.
 *
 * Implementations own the browser or host-specific delivery details. Core/session code should send and receive only
 * Wokwi protocol payloads, without browser topics, JCEF objects, or IntelliJ UI types.
 */
interface WokwiTransport {

    fun send(message: String)

    fun subscribe(listener: Listener)

    fun removeSubscriber(listener: Listener)

    fun dispose()

    interface Listener {
        fun messageReceived(message: String): Boolean
    }
}
