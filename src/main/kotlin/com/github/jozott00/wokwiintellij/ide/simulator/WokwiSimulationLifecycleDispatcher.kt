package com.github.jozott00.wokwiintellij.ide.simulator

import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

/**
 * Dispatches IDE-level simulation lifecycle events to subscribers.
 *
 * These events carry UI objects and intentionally stay outside core session events.
 */
class WokwiSimulationLifecycleDispatcher {
    private val persistentSubscribers = CopyOnWriteArrayList<WokwiSimulationLifecycleListener>()

    fun subscribePersistent(listener: WokwiSimulationLifecycleListener) {
        if (persistentSubscribers.contains(listener)) return

        persistentSubscribers.add(listener)
    }

    fun simulationViewReady(component: JComponent) {
        notifySubscribers { it.onSimulationViewReady(component) }
    }

    fun simulationStopped() {
        notifySubscribers { it.onSimulationStopped() }
    }

    private fun notifySubscribers(event: (WokwiSimulationLifecycleListener) -> Unit) {
        for (listener in persistentSubscribers) {
            event(listener)
        }
    }
}

interface WokwiSimulationLifecycleListener {
    fun onSimulationViewReady(component: JComponent) {}

    fun onSimulationStopped() {}
}
