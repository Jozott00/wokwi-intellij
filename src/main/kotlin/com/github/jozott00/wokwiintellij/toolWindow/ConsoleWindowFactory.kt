package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.services.WokwiComponentService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ConsoleWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val componentService = toolWindow.project.service<WokwiComponentService>()

        val consoleToolWindow = componentService.consoleToolWindowComponent
        val content = ContentFactory.getInstance()
            .createContent(consoleToolWindow, null, false)

        toolWindow.contentManager.addContent(content)
    }

}
