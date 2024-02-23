package com.github.jozott00.wokwiintellij.runner.profileStates

import com.github.jozott00.wokwiintellij.runner.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class WokwiSimulatorStartState(private val project: Project, private val waitForDebugger: Boolean) : RunProfileState {
    override fun execute(executor: Executor?, runner: ProgramRunner<*>) = object : ExecutionResult {
        override fun getExecutionConsole() = null

        override fun getActions(): Array<AnAction> {
            return emptyArray()
        }

        override fun getProcessHandler() = WokwiStartProcessHandler(project, waitForDebugger)
//        override fun getProcessHandler() = WokwiRunnerProcessHandler(project)
    }
}


class WokwiStartProcessHandler(project: Project, waitForDebugger: Boolean) :
    WokwiProcessHandler() {

    private val wokwiService = project.service<WokwiProjectService>()

    init {
        wokwiService.startSimulator(this, waitForDebugger)
    }

    override fun destroyProcessImpl() {
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        notifyProcessDetached()
    }

    override fun detachIsDefault() = false

    override fun getProcessInput() = null

    override fun onStarted(runArgs: WokwiArgs) {
        this.destroyProcess()
    }
}