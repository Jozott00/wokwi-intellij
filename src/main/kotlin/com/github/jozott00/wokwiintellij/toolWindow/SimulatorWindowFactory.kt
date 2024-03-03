package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.services.WokwiComponentService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

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