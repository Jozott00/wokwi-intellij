package com.github.jozott00.wokwiintellij.ui.console

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import javax.swing.JPanel


class SimulationConsole(project: Project) : JPanel(), Disposable {

    private val consoleView: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    private val ansiEscapeDecoder = AnsiEscapeDecoder()

    init {
        Disposer.register(this, consoleView)

        this.layout = BorderLayout()
        add(consoleView.component)
    }

    fun appendLog(bytes: ByteArray) {
        val str = String(bytes, Charsets.UTF_8)
        ansiEscapeDecoder.escapeText(str, ProcessOutputTypes.STDOUT) { text, contentType ->
            consoleView.print(text, ConsoleViewContentType.getConsoleViewType(contentType))
        }
    }

    fun clear() {
        consoleView.clear()
    }

    override fun dispose() {
        // nothing to do
    }
}