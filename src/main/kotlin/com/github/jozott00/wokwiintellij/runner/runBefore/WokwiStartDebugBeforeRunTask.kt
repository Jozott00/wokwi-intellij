package com.github.jozott00.wokwiintellij.runner.runBefore

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.intellij.codeInsight.util.GlobalInspectionScope
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.swing.Icon

class WokwiStartDebugBeforeRunTaskProvider : BeforeRunTaskProvider<WokwiStartDebugBeforeRunTask>() {

    companion object {
        val ID: Key<WokwiStartDebugBeforeRunTask> = Key.create("WokwiStartDebug.Before.Run")
    }

    override fun getId(): Key<WokwiStartDebugBeforeRunTask> = ID

    override fun getName() = "Start Wokwi Debug"

    override fun getIcon(): Icon = WokwiIcons.Debug

    override fun createTask(runConfiguration: RunConfiguration): WokwiStartDebugBeforeRunTask =
        WokwiStartDebugBeforeRunTask()

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: WokwiStartDebugBeforeRunTask
    ): Boolean {
        val projectService = environment.project.service<WokwiProjectService>()
        return runBlocking {
            val result = projectService.startSimulatorSuspended(task, true)
            task.waitForSimulatorToBeRunning()
            result
        }
    }

}

class WokwiStartDebugBeforeRunTask :
    BeforeRunTask<WokwiStartDebugBeforeRunTask>(WokwiStartDebugBeforeRunTaskProvider.ID), WokwiSimulatorListener {

    private val simulatorRunning = CompletableDeferred<Unit>()

    suspend fun waitForSimulatorToBeRunning() {
        simulatorRunning.await()
    }

    override fun onRunning() {
        simulatorRunning.complete(Unit)
    }
}
