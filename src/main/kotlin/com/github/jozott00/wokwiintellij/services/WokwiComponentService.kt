package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.states.WokwiConfigState
import com.github.jozott00.wokwiintellij.toolWindow.SimulatorPanel
import com.github.jozott00.wokwiintellij.toolWindow.WokwiConsoleToolWindow
import com.github.jozott00.wokwiintellij.toolWindow.WokwiSimulationToolWindow
import com.github.jozott00.wokwiintellij.toolWindow.wokwiConfigPanel
import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project


@Service(Service.Level.PROJECT)
class WokwiComponentService(val project: Project) {

    val wokwiConfigState = project.service<WokwiConfigState>()

    val configPanel = wokwiConfigPanel(wokwiConfigState.state) {
        onChangeAction = {
            // do nothing
        }
    }

    val simulatorToolWindow = WokwiSimulationToolWindow(configPanel)
    val consoleToolWindow = WokwiConsoleToolWindow(project)


}