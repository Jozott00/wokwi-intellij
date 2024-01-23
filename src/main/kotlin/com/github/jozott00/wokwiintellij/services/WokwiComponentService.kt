package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.states.WokwiConfigState
import com.github.jozott00.wokwiintellij.toolWindow.SimulatorPanel
import com.github.jozott00.wokwiintellij.toolWindow.WokwiToolWindow
import com.github.jozott00.wokwiintellij.toolWindow.wokwiConfigPanel
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import javax.swing.JPanel


@Service(Service.Level.PROJECT)
class WokwiComponentService(val project: Project) {

    val wokwiConfigState = project.service<WokwiConfigState>()

    val simulatorPanel = SimulatorPanel()
    val configPanel = wokwiConfigPanel(wokwiConfigState.state) {
        onChangeAction = {
            println("Changes in model: ${wokwiConfigState.state}")
        }
    }

    val simulatorHttpPanel: SimulatorJCEFHtmlPanel by lazy { SimulatorJCEFHtmlPanel() }
    val toolWindow = WokwiToolWindow(configPanel, simulatorHttpPanel)


}