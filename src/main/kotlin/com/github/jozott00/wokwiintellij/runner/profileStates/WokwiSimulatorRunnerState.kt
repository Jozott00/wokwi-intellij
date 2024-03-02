package com.github.jozott00.wokwiintellij.runner.profileStates

import com.github.jozott00.wokwiintellij.runner.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.testFramework.processAllServiceDescriptors
import com.jetbrains.rd.generator.nova.array
import java.io.OutputStream

class WokwiSimulatorRunnerState(private val myEnvironment: ExecutionEnvironment) : CommandLineState(myEnvironment) {
    private val projectService = myEnvironment.project.service<WokwiProjectService>()
    private val am = ActionManager.getInstance()
    override fun startProcess() = projectService.startSimulatorNew(false)

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


//@Deprecated("The WokwiRunner is currently not used, and might be removed in the future.")
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