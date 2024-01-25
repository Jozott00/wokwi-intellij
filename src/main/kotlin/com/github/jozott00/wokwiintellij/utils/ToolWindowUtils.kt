package com.github.jozott00.wokwiintellij.utils

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

object ToolWindowUtils {

    fun setSimulatorIcon(project: Project, live: Boolean) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(WokwiConstants.TOOL_WINDOW_SIM_ID)
        var icon = WokwiIcons.ToolWindowIcon
        if (live)
            icon = ExecutionUtil.getLiveIndicator(icon)
        toolWindow?.setIcon(icon)
    }

}