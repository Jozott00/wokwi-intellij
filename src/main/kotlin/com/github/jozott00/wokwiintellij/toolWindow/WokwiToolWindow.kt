package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.intellij.openapi.ui.DialogPanel
import java.awt.BorderLayout
import javax.swing.JPanel

class WokwiToolWindow(private val configPanel: DialogPanel, private val simulationPanel: SimulatorJCEFHtmlPanel) {

    private val panel = JPanel(BorderLayout()).apply {
        this.add(simulationPanel.component)
    }

    fun getContent() = panel

    fun showSimulation() {
        if (panel.components.contains(simulationPanel.component)) return
        panel.removeAll()
//        panel.add(simulationPanel)
//        simulationPanel.loadSimulator()
        panel.revalidate()
        panel.repaint()
    }

    fun showConfig() {
        if (panel.components.contains(configPanel)) return
        panel.removeAll()
        panel.add(configPanel)
//        simulationPanel.stopSimulator()
        panel.revalidate()
        panel.repaint()
    }


}