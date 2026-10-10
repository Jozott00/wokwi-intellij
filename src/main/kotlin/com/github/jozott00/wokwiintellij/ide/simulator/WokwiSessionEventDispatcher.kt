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

    /** The controller can atomically ignore callbacks from a retired runtime. */
    fun asSessionListener(dispatch: (() -> Unit) -> Unit = { it() }): WokwiSession.Listener = object : WokwiSession.Listener {
        private fun notifySubscribers(event: (WokwiSession.Listener) -> Unit) {
            dispatch { this@WokwiSessionEventDispatcher.notifySubscribers(event) }
        }

        override fun onStarted(config: WokwiSessionStartConfig) {
            notifySubscribers { it.onStarted(config) }
        }

        override fun onRunning() {
            notifySubscribers { it.onRunning() }
        }

        override fun onDebuggerReady() { notifySubscribers { it.onDebuggerReady() } }
        override fun onPaused() { notifySubscribers { it.onPaused() } }
        override fun onStopped() { notifySubscribers { it.onStopped() } }
        override fun onTerminated() { notifySubscribers { it.onTerminated() } }
        override fun onResourceError(message: InboundMessage.LoadResource, error: Throwable) {
            notifySubscribers { it.onResourceError(message, error) }
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

        override fun onSwitchToBase64Requested() {
            notifySubscribers { it.onSwitchToBase64Requested() }
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

    fun unsubscribe(listener: WokwiSession.Listener) {
        sessionSubscribers.remove(listener)
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

    private fun notifySubscribers(event: (WokwiSession.Listener) -> Unit) {
        for (listener in persistentSubscribers) {
            event(listener)
        }
        for (listener in sessionSubscribers) {
            event(listener)
        }
    }
}
