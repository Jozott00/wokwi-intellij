package com.github.jozott00.wokwiintellij.toolWindow

import com.intellij.openapi.ui.DialogPanel
import java.awt.BorderLayout
import javax.swing.JPanel

class WokwiToolWindow(private val configPanel: DialogPanel, private val simulationPanel: SimulatorPanel) {

    private val panel = JPanel(BorderLayout()).apply {
        this.add(configPanel)

    }

    fun getContent() = panel

    fun showSimulation() {
        if (panel.components.contains(simulationPanel)) return
        panel.removeAll()
        panel.add(simulationPanel)
        simulationPanel.loadSimulator()
        panel.revalidate()
    }

    fun showConfig() {
        if (panel.components.contains(configPanel)) return
        panel.removeAll()
        panel.add(configPanel)
        simulationPanel.stopSimulator()
        panel.revalidate()
    }


}