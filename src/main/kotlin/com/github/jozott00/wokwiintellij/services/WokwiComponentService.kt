package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.toolWindow.WokwiConsoleToolWindow
import com.github.jozott00.wokwiintellij.toolWindow.WokwiSimulationToolWindow
import com.github.jozott00.wokwiintellij.toolWindow.wokwiConfigPanel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project


@Service(Service.Level.PROJECT)
class WokwiComponentService(val project: Project) {

    val wokwiConfigState = project.service<WokwiSettingsState>()

    val configPanel = wokwiConfigPanel(wokwiConfigState.state) {
        onChangeAction = {
            // do nothing
        }
    }

    val simulatorToolWindowComponent = WokwiSimulationToolWindow(configPanel)
    val consoleToolWindowComponent = WokwiConsoleToolWindow(project)


}