package com.github.jozott00.wokwiintellij.execution.profileStates

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service

class WokwiSimulatorRunnerState(private val myEnvironment: ExecutionEnvironment) : CommandLineState(myEnvironment) {
    private val projectService = myEnvironment.project.service<WokwiProjectService>()
    private val am = ActionManager.getInstance()
    override fun startProcess() = projectService.startSimulator(false)

    override fun createActions(console: ConsoleView?, processHandler: ProcessHandler?, executor: Executor?) = arrayOf(
        Separator(),
        am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiWatchAction")
    )

    override fun createConsole(executor: Executor): ConsoleView? {
        val builder = consoleBuilder ?: return null
        builder.setViewer(true) // make it readonly
        return builder.console
    }

}

