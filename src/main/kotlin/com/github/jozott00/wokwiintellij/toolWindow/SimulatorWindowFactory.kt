package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.actions.WokwiRestartAction
import com.github.jozott00.wokwiintellij.services.WokwiComponentService
import com.github.jozott00.wokwiintellij.services.WokwiRootService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class SimulatorWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val componentService = toolWindow.project.service<WokwiComponentService>()
        val rootService = toolWindow.project.service<WokwiRootService>()

//        val toolWindowContent = componentService.toolWindow.getContent()
        val toolWindowContent = rootService.newSimulation()

        val content = ContentFactory.getInstance()
            .createContent(toolWindowContent, null, false)
        toolWindow.contentManager.addContent(content)

    }

    override fun shouldBeAvailable(project: Project) = true

}