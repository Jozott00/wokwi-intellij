package com.github.jozott00.wokwiintellij.ide.actions

import com.github.jozott00.wokwiintellij.ide.simulator.WokwiSessionController
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class WokwiRestartAction : AnAction() {
    override fun actionPerformed(p0: AnActionEvent) {
        p0.project?.service<WokwiSessionController>()?.startSimulator()
    }

}
