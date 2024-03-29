package com.github.jozott00.wokwiintellij.utils.simulation

import com.github.jozott00.wokwiintellij.execution.WokwiConfigurationFactory
import com.github.jozott00.wokwiintellij.execution.processHandler.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.execution.configs.WokwiRunConfigType
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project



object SimulatorRunUtils {

    /**
     * Starts the execution of the Wokwi Simulator for the given project by executing the Wokwi run configuration.
     *
     * @param project The project in which the Simulator is to be executed.
     */
    fun startExecution(project: Project) {
        val config = RunManager.getInstance(project)
            .createConfiguration("Wokwi Simulator", WokwiConfigurationFactory(WokwiRunConfigType()))
        val builder = ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), config)

        if (builder == null) {
            WokwiNotifier.notifyBalloon("Failed to start Wokwi simulator", "Couldn't create execution environment", NotificationType.ERROR)
            return
        }

        ProgramRunnerUtil.executeConfiguration(builder.build(), true, true)
    }

    fun startExecutionIfNotRunning(project: Project) {
        val runningProcess = ExecutionManager.getInstance(project).getRunningProcesses().find {
            !it.isProcessTerminated && !it.isProcessTerminating && it is WokwiProcessHandler
        }

        if (runningProcess == null) {
            startExecution(project)
        }
    }
}