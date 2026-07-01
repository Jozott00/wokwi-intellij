package com.github.jozott00.wokwiintellij.ide.simulator

import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

class WokwiSimulationLifecycleDispatcherTest {

    @Test
    fun `dispatches simulation lifecycle events to persistent subscribers`() {
        val dispatcher = WokwiSimulationLifecycleDispatcher()
        val listener = RecordingLifecycleListener()
        val component = JPanel()

        dispatcher.subscribePersistent(listener)

        dispatcher.simulationViewReady(component)
        dispatcher.simulationStopped()

        assertEquals(listOf<Any>(component, "stopped"), listener.events)
    }

    @Test
    fun `does not subscribe the same lifecycle listener twice`() {
        val dispatcher = WokwiSimulationLifecycleDispatcher()
        val listener = RecordingLifecycleListener()

        dispatcher.subscribePersistent(listener)
        dispatcher.subscribePersistent(listener)

        dispatcher.simulationStopped()

        assertEquals(listOf<Any>("stopped"), listener.events)
    }

    private class RecordingLifecycleListener : WokwiSimulationLifecycleListener {
        val events = mutableListOf<Any>()

        override fun onSimulationViewReady(component: javax.swing.JComponent) {
            events += component
        }

        override fun onSimulationStopped() {
            events += "stopped"
        }
    }
}
