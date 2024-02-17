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
        val result = environment.project.service<WokwiProjectService>()
            .startSimulatorSynchronous(task, true)

        task.waitForSimulatorToBeRunning()
        return result
    }

}

class WokwiStartDebugBeforeRunTask :
    BeforeRunTask<WokwiStartDebugBeforeRunTask>(WokwiStartDebugBeforeRunTaskProvider.ID), WokwiSimulatorListener {

    private val monitor = java.lang.Object()

    @Volatile
    private var isSimulatorRunning = false

    fun waitForSimulatorToBeRunning() {
        synchronized(monitor) {
            while (!isSimulatorRunning) {
                try {
                    monitor.wait()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }

    override fun onRunning() {
        synchronized(monitor) {
            isSimulatorRunning = true
            monitor.notifyAll()
        }
    }
}