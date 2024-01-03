package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.actions.WokwiRestartAction
import com.github.jozott00.wokwiintellij.services.WokwiComponentService
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.panel
import org.jdesktop.swingx.action.ActionManager
import javax.swing.JPanel

class SimulatorWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val componentService = toolWindow.project.service<WokwiComponentService>()

        val toolWindowContent = componentService.toolWindow.getContent()
        val content = ContentFactory.getInstance()
            .createContent(toolWindowContent, null, false)
        toolWindow.contentManager.addContent(content)

    }

    override fun shouldBeAvailable(project: Project) = true

}