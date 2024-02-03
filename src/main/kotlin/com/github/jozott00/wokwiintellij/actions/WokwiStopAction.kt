package com.github.jozott00.wokwiintellij.actions


import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class WokwiStopAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val s = event.project?.service<WokwiProjectService>()
        s?.stopSimulator()

    }
}