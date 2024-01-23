package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class WokwiSimulationToolWindow(private val configPanel: DialogPanel) :
    JPanel() {

    init {
        this.layout = BorderLayout()
        this.add(configPanel)
    }

    fun showSimulation(simulator: JComponent) {
        this.removeAll()
        this.add(simulator)
        this.repaint()
    }

    fun showConfig() {
        this.removeAll()
        this.add(configPanel)
        this.repaint()
    }

}