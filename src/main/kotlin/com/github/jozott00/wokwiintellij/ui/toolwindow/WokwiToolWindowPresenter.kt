package com.github.jozott00.wokwiintellij.ui.toolwindow

import com.github.jozott00.wokwiintellij.ide.simulator.WokwiSimulationLifecycleListener
import com.github.jozott00.wokwiintellij.utils.ToolWindowUtils
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class WokwiToolWindowPresenter(
    private val project: Project,
    private val toolWindow: () -> WokwiToolWindowPanel,
) : WokwiSimulationLifecycleListener {

    override fun onSimulationViewReady(component: JComponent) {
        invokeLater {
            toolWindow().showSimulation(component)
            ToolWindowUtils.setSimulatorIcon(project, true)
        }
    }

    override fun onSimulationStopped() {
        invokeLater {
            ToolWindowUtils.setSimulatorIcon(project, false)
            toolWindow().showConfig()
        }
    }
}
