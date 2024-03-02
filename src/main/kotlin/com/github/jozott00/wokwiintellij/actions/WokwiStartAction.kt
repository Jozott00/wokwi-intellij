package com.github.jozott00.wokwiintellij.actions


import com.github.jozott00.wokwiintellij.runner.WokwiConfigurationFactory
import com.github.jozott00.wokwiintellij.runner.configs.WokwiRunConfigType
import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class WokwiStartAction : AnAction() {


    // TODO: Consider run configuration instead of custom run handling
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let {
            val config = RunManager.getInstance(it).createConfiguration("Wokwi Simulator", WokwiConfigurationFactory(
                WokwiRunConfigType()
            ))
            ProgramRunnerUtil.executeConfiguration(config, DefaultRunExecutor.getRunExecutorInstance())
        }
//        val s = event.project?.service<WokwiProjectService>()
//        s?.startSimulator()
    }
}