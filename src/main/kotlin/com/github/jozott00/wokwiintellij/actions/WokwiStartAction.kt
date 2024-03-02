package com.github.jozott00.wokwiintellij.actions


import com.github.jozott00.wokwiintellij.runner.WokwiConfigurationFactory
import com.github.jozott00.wokwiintellij.runner.configs.WokwiRunConfigType
import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger

class WokwiStartAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let {
            val config = RunManager.getInstance(it).createConfiguration("Wokwi Simulator", WokwiConfigurationFactory(
                WokwiRunConfigType()
            ))
            val builder = ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), config)

            if (builder == null) {
                WokwiNotifier.notifyBalloon("Failed to start Wokwi simulator", "Couldn't create execution environment", NotificationType.ERROR)
                return@let
            }

            ProgramRunnerUtil.executeConfiguration(builder.build(), true, true)
        }
    }
}