package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.toolWindow.WokwiSimulationToolWindow
import com.github.jozott00.wokwiintellij.ui.config.wokwiConfigPanel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope


@Service(Service.Level.PROJECT)
class WokwiComponentService(val project: Project, val cs: CoroutineScope) {

    private val configPanel by lazy {
        val wokwiConfigState = project.service<WokwiSettingsState>()
        wokwiConfigPanel(project, cs, wokwiConfigState) {
            onChangeAction = {
                // do nothing
            }
        }
    }

    val simulatorToolWindowComponent by lazy { WokwiSimulationToolWindow(configPanel) }
}