package com.github.jozott00.wokwiintellij.actions

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.states.WokwiConfigState
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service

class WokwiWatchAction : ToggleAction() {

    override fun isSelected(p0: AnActionEvent): Boolean {
        return p0.project?.service<WokwiConfigState>()?.state?.watchElf ?: false
    }

    override fun setSelected(even: AnActionEvent, watchEnabled: Boolean) {
        even.project?.service<WokwiConfigState>()?.state?.watchElf = watchEnabled
        if (watchEnabled) {
            even.project?.service<WokwiProjectService>()?.watchStart()
        } else {
            even.project?.service<WokwiProjectService>()?.watchStop()
        }
    }

}