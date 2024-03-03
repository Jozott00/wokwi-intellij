package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.services.WokwiComponentService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SimulatorWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val componentService = toolWindow.project.service<WokwiComponentService>()

        val toolWindowContent = componentService.simulatorToolWindowComponent

        val content = ContentFactory.getInstance()
            .createContent(toolWindowContent, null, false)

        toolWindow.contentManager.addContent(content)

    }

    override fun shouldBeAvailable(project: Project) = true
}

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