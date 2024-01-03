package com.github.jozott00.wokwiintellij.toolWindow


import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.util.preferredWidth
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.JProgressBar

class SimulatorPanel : JPanel() {

    private val cardLayout = CardLayout()
    private var browser: JCEFContent? = null

    private val loadingPanel = panel {
        panel {
            row {
                cell(JProgressBar().apply {
                    isIndeterminate = true
                    preferredWidth = 300
                })
                    .label("Starting simulator...", LabelPosition.TOP)

            }
        }.align(Align.CENTER)

    }.withVisualPadding()


    init {
        layout = cardLayout
        add(loadingPanel)
        add("LOADING", loadingPanel)
        cardLayout.show(this, "LOADING")
    }


    fun loadSimulator() {
        browser = JCEFContent { browser ->
            cardLayout.show(this, "SIMULATOR")
            revalidate()
            repaint()
        }

        val simulator = simulator(toolbar(), browser!!)
        add("SIMULATOR", simulator)
        revalidate()
        repaint()
    }

    fun stopSimulator() {
        remove(browser)
        browser?.dispose()

        cardLayout.show(this, "LOADING")

        revalidate()
        repaint()
    }

    private fun simulator(toolbar: JPanel, browser: JPanel): JPanel {
        val simulator = JPanel(BorderLayout())
        simulator.add(toolbar, BorderLayout.NORTH)
        simulator.add(browser!!, BorderLayout.CENTER)

        return simulator
    }

    private fun toolbar(): JPanel {

        val panel = JPanel(BorderLayout())

        val am = ActionManager.getInstance()
        val group = DefaultActionGroup(
            am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiStopAction"),
            am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiRestartAction"),
            am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiWatchAction"),
        )
        val toolbar = am.createActionToolbar("com.github.jozott00.wokwiintellij.actions.WokwiToolbar", group, false)
        // horizonal orientation
        toolbar.orientation = 0
        toolbar.targetComponent = panel
        panel.add(toolbar.component, BorderLayout.NORTH)


        return panel
    }

    private fun updateLayout() {
        layout = CardLayout()
    }
}