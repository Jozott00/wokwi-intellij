package com.github.jozott00.wokwiintellij.execution.processHandler

import com.github.jozott00.wokwiintellij.services.WokwiSimulatorService
import com.github.jozott00.wokwiintellij.simulator.SimExitCode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.OutputStream

class WokwiRunProcessHandler(project: Project): WokwiProcessHandler() {

    private val projectService: WokwiSimulatorService = project.service()

    override fun destroyProcessImpl() {
        projectService.stopSimulator()
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? {
        return null
    }

    override fun onTextAvailable(text: String, outputType: Key<*>) {
        notifyTextAvailable(text, outputType)
    }

    override fun onShutdown(exitCode: SimExitCode) {
        if (!isProcessTerminated) {
            notifyProcessTerminated(exitCode.int)
        }
    }

}