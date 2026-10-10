package com.github.jozott00.wokwiintellij.ide.execution.runBefore

import com.github.jozott00.wokwiintellij.ide.simulator.WokwiSessionController
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.github.jozott00.wokwiintellij.utils.simulation.SimulatorRunUtils
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.Icon

/**
 * A [BeforeRunTaskProvider] implementation that initiates a debug session by starting the Wokwi
 * simulator in debug mode before executing the main run configuration.
 *
 * This is required when debugging with CLion's `Remote Debug` configuration.
 */
class WokwiStartDebugBeforeRunTaskProvider : BeforeRunTaskProvider<WokwiStartDebugBeforeRunTask>() {

    override fun getId(): Key<WokwiStartDebugBeforeRunTask> = ID

    override fun getName() = "Start Wokwi Debug"

    override fun getIcon(): Icon = WokwiIcons.Debug

    override fun createTask(runConfiguration: RunConfiguration): WokwiStartDebugBeforeRunTask =
        WokwiStartDebugBeforeRunTask()


    /**
     * Executes the task to start the Wokwi simulator in debug mode before the main run configuration.
     * It ensures the simulator is running and ready for debugging. The additional execution of the
     * run configuration is required to provide simulation output in a Run-window.
     *
     * @param context The data context in which the task is executed.
     * @param configuration The run configuration associated with the task.
     * @param environment The execution environment for the task.
     * @param task The [WokwiStartDebugBeforeRunTask] to be executed.
     * @return `true` if the task was successfully executed, `false` otherwise.
     */
    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: WokwiStartDebugBeforeRunTask
    ): Boolean {
        val projectService = environment.project.service<WokwiSessionController>()
        // CLion's legacy before-run executor may supply neither a Job nor an indicator.
        // Propagate cancellation when supplied, and retain the controller's bounded wait otherwise.
        return runBlockingMaybeCancellable {
            withContext(Dispatchers.IO) {
                val result = projectService.startDebuggerAndAwaitReady()
                if (result) SimulatorRunUtils.startExecutionIfNotRunning(environment.project)
                result
            }
        }
    }

}

/** Marker persisted in the debugger's before-run configuration. Each execution creates a fresh readiness wait. */
class WokwiStartDebugBeforeRunTask : BeforeRunTask<WokwiStartDebugBeforeRunTask>(ID)

val ID: Key<WokwiStartDebugBeforeRunTask> = Key.create("WokwiStartDebug.Before.Run")
