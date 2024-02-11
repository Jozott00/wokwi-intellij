package com.github.jozott00.wokwiintellij.runner

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.OutputStream

class WokwiProcessHandler(val project: Project) : ProcessHandler(), WokwiSimulatorListener {

    val wokwiService = project.service<WokwiProjectService>()

    override fun startNotify() {
        super.startNotify()

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "My Task", false) {
                override fun run(indicator: ProgressIndicator) {
                    wokwiService.startSimulator(this@WokwiProcessHandler)
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