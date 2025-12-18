package com.github.jozott00.wokwiintellij.execution.runBefore

import com.github.jozott00.wokwiintellij.services.WokwiSimulatorService
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.github.jozott00.wokwiintellij.utils.simulation.SimulatorRunUtils
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import kotlinx.coroutines.*
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
        val projectService = environment.project.service<WokwiSimulatorService>()
        return runBlocking(Dispatchers.IO) {
            // start child scope to make cancellation on dispose possible.
            val job = projectService.childScope().async {
                val result = projectService.startSimulatorAsync(task, true)
                task.waitForSimulatorToBeRunning()
                result
            }
            val result = job.await()

            // start run execution for additional log output
            SimulatorRunUtils.startExecutionIfNotRunning(environment.project)

            result
        }
    }

}

/**
 * A [BeforeRunTask] implementation that waits for the Wokwi simulator to start and reach a running state
 * before allowing the debug session to proceed. It implements the [WokwiSimulatorListener] interface
 * to receive notifications about the simulator's state.
 */
class WokwiStartDebugBeforeRunTask :
    BeforeRunTask<WokwiStartDebugBeforeRunTask>(ID), WokwiSimulatorListener {

    private val simulatorRunning = CompletableDeferred<Unit>()

    /**
     * Suspends the current coroutine until the Wokwi simulator has been reported as running.
     * This method waits for the [onRunning] event to be called, indicating that the simulator
     * is ready.
     */
    suspend fun waitForSimulatorToBeRunning() {
        simulatorRunning.await()
    }

    /**
     * Callback method from the [WokwiSimulatorListener] interface. It is called when the simulator
     * changes its state to running.
     *
     * This method completes the [simulatorRunning] deferred, allowing any suspended coroutines
     * waiting for the simulator to start to resume execution.
     */
    override fun onRunning() {
        simulatorRunning.complete(Unit)
    }
}

val ID: Key<WokwiStartDebugBeforeRunTask> = Key.create("WokwiStartDebug.Before.Run")