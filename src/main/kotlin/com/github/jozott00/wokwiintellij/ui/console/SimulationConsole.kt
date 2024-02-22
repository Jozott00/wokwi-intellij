package com.github.jozott00.wokwiintellij.ui.console

import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleImpl.ConsoleEditorsPanel
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.awt.BorderLayout
import javax.swing.JPanel


class SimulationConsole(project: Project) : JPanel(), Disposable, WokwiSimulatorListener {

    private var executionRunning = false;

    private val consoleView: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
//    private val consoleView = LanguageConsoleBuilder()
//        .executionEnabled { this@SimulationConsole.executionRunning }
//        .oneLineInput(true)
//        .build(project, PlainTextLanguage.INSTANCE)

    init {
        Disposer.register(this, consoleView)

        this.layout = BorderLayout()
        add(consoleView.component)
    }

    override fun onTextAvailable(text: String, outputType: Key<*>) {
        consoleView.print(text, ConsoleViewContentType.getConsoleViewType(outputType))
    }

    override fun onStarted(runArgs: WokwiArgs) {
        executionRunning = true
        consoleView.clear()
    }

    override fun onShutdown() {
        executionRunning = false
    }

    override fun dispose() {
        // nothing to do
    }
}