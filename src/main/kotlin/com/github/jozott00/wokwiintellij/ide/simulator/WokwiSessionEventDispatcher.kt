package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.ports.GdbEvent
import com.github.jozott00.wokwiintellij.core.protocol.InboundDecodeResult
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.core.session.WokwiSessionStartConfig
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Dispatches events from one active [WokwiSession] listener to IDE-side subscribers.
 */
class WokwiSessionEventDispatcher {
    private val sessionSubscribers = CopyOnWriteArrayList<WokwiSession.Listener>()
    private val persistentSubscribers = CopyOnWriteArrayList<WokwiSession.Listener>()

    private val sessionListener = object : WokwiSession.Listener {
        override fun onStarted(config: WokwiSessionStartConfig) {
            notifySubscribers { it.onStarted(config) }
        }

        override fun onRunning() {
            notifySubscribers { it.onRunning() }
        }

        override fun onUartData(bytes: ByteArray) {
            notifySubscribers { it.onUartData(bytes) }
        }

        override fun onChipOutput(chipName: String, message: String) {
            notifySubscribers { it.onChipOutput(chipName, message) }
        }

        override fun onGdbError(error: GdbEvent.Error) {
            notifySubscribers { it.onGdbError(error) }
        }

        override fun onMalformedMessage(message: InboundDecodeResult.Malformed) {
            notifySubscribers { it.onMalformedMessage(message) }
        }

        override fun onUnknownMessage(message: InboundMessage.Unknown) {
            notifySubscribers { it.onUnknownMessage(message) }
        }

        override fun onUnsupportedMessage(message: InboundMessage) {
            notifySubscribers { it.onUnsupportedMessage(message) }
        }
    }

    /**
     * Registers a session-scoped subscriber. Session-scoped subscribers are cleared when the active runtime is replaced.
     */
    fun subscribe(listener: WokwiSession.Listener) {
        if (sessionSubscribers.contains(listener)) return

        sessionSubscribers.add(listener)
    }

    /**
     * Registers a subscriber that survives runtime replacement.
     */
    fun subscribePersistent(listener: WokwiSession.Listener) {
        if (persistentSubscribers.contains(listener)) return

        persistentSubscribers.add(listener)
    }

    /**
     * Clears session-scoped subscribers while preserving persistent subscribers.
     */
    fun clearSessionSubscribers() {
        sessionSubscribers.clear()
    }

    /**
     * Returns the listener attached to the active [WokwiSession].
     */
    fun asSessionListener(): WokwiSession.Listener = sessionListener

    private fun notifySubscribers(event: (WokwiSession.Listener) -> Unit) {
        for (listener in persistentSubscribers) {
            event(listener)
        }
        for (listener in sessionSubscribers) {
            event(listener)
        }
    }
}
