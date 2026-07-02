package com.github.jozott00.wokwiintellij.ide.execution.processHandler

import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.ide.simulator.WokwiSessionController
import com.github.jozott00.wokwiintellij.ide.simulator.SimExitCode
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.io.OutputStream

class WokwiRunProcessHandler(project: Project): WokwiProcessHandler(), WokwiSession.Listener {

    private val projectService: WokwiSessionController = project.service()
    private val ansiEscapeDecoder = AnsiEscapeDecoder()

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

    override fun onUartData(bytes: ByteArray) {
        if (bytes.isEmpty()) return

        notifyConsoleText(String(bytes, Charsets.UTF_8))
    }

    override fun onChipOutput(chipName: String, message: String) {
        notifyConsoleText("[$chipName] $message\n")
    }

    override fun onShutdown(exitCode: SimExitCode) {
        if (!isProcessTerminated) {
            notifyProcessTerminated(exitCode.int)
        }
    }

    private fun notifyConsoleText(text: String) {
        ansiEscapeDecoder.escapeText(text, ProcessOutputTypes.STDOUT) { decodedText, contentType ->
            notifyTextAvailable(decodedText, contentType)
        }
    }
}
