package com.github.jozott00.wokwiintellij.actions


import com.github.jozott00.wokwiintellij.utils.simulation.SimulatorRunUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class WokwiStartAction : AnAction() {


    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { SimulatorRunUtils.startExecution(it) }
    }
}