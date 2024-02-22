package com.github.jozott00.wokwiintellij.runner.profileStates

import com.github.jozott00.wokwiintellij.runner.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.OutputStream

//@Deprecated("The WokwiRunner is currently not used, and might be removed in future.")
class WokwiSimulatorRunnerState(val myEnvironment: ExecutionEnvironment) : CommandLineState(myEnvironment) {
    override fun startProcess() = WokwiRunnerProcessHandler(myEnvironment.project)
}

//@Deprecated("The WokwiRunner is currently not used, and might be removed in future.")
class WokwiRunnerProcessHandler(val project: Project) : WokwiProcessHandler() {

    val wokwiService = project.service<WokwiProjectService>()

    override fun startNotify() {
        super.startNotify()

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "Wokwi execution", false) {
                override fun run(indicator: ProgressIndicator) {
                    wokwiService.startSimulator(this@WokwiRunnerProcessHandler, false)
                }
            },
            ProgressIndicatorBase()
        )

    }

    override fun onShutdown() {
        destroyProcess()
    }

    override fun onTextAvailable(text: String, outputType: Key<*>) {
        notifyTextAvailable(text, outputType)
    }

    override fun destroyProcessImpl() {
        thisLogger().info("Destroy Process")
        wokwiService.stopSimulator()
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        thisLogger().info("Detach Process")
        notifyProcessDetached()
    }

    override fun detachIsDefault() = false

    override fun getProcessInput(): OutputStream {
        thisLogger().info("Ouput stream")
        val stream = object : OutputStream() {
            override fun write(b: Int) {
                thisLogger().info("Got new input $b")
            }
        }
        return stream
    }


}