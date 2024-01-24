package com.github.jozott00.wokwiintellij.utils

import com.github.jozott00.wokwiintellij.Constants
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.generator.nova.PredefinedType

object ToolWindowUtils {

    fun setSimulatorIcon(project: Project, live: Boolean) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(Constants.TOOL_WINDOW_SIM_ID)
        var icon = WokwiIcons.ToolWindowIcon
        if (live)
            icon = ExecutionUtil.getLiveIndicator(icon)
        toolWindow?.setIcon(icon)
    }

}