package com.github.jozott00.wokwiintellij.actions


import com.github.jozott00.wokwiintellij.runner.WokwiConfigurationFactory
import com.github.jozott00.wokwiintellij.runner.configs.WokwiRunConfigType
import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.github.jozott00.wokwiintellij.utils.simulation.startSimulatorRunExecution
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
        event.project?.let { startSimulatorRunExecution(it) }
    }
}