package com.github.jozott00.wokwiintellij.ui.toolwindow

import com.intellij.openapi.ui.DialogPanel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class WokwiToolWindowPanel(private val configPanel: DialogPanel) : JPanel() {

    init {
        layout = BorderLayout()
        add(configPanel)
    }

    fun showSimulation(simulator: JComponent) {
        removeAll()
        add(simulator)
        repaint()
    }

    fun showConfig() {
        removeAll()
        add(configPanel)
        repaint()
    }
}
