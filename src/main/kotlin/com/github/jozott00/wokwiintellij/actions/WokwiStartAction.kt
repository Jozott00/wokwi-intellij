package com.github.jozott00.wokwiintellij.actions


import com.github.jozott00.wokwiintellij.services.WokwiSimulatorService
import com.github.jozott00.wokwiintellij.utils.simulation.SimulatorRunUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class WokwiStartAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.project?.let {
            e.presentation.isEnabled = !it.service<WokwiSimulatorService>().isSimulatorRunning()
        }
    }
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { SimulatorRunUtils.startExecution(it) }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}