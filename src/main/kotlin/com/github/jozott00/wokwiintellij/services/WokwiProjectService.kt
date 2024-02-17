package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.runner.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.github.jozott00.wokwiintellij.toolWindow.ConsoleWindowFactory
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.github.jozott00.wokwiintellij.utils.ToolWindowUtils
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.github.jozott00.wokwiintellij.utils.resolveWith
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import kotlin.io.path.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class WokwiProjectService(val project: Project) : Disposable {

    private var simulator: WokwiSimulator? = null
    private var console: SimulationConsole? = null

    private val componentService = project.service<WokwiComponentService>()
    private val settingsState = project.service<WokwiSettingsState>()
    private val licensingService = ApplicationManager.getApplication().service<WokwiLicensingService>()
    private val argsLoader = project.service<WokwiArgsLoader>()
    private var consoleToolWindow: ToolWindow? = null

    fun startSimulator(byProcess: WokwiProcessHandler? = null, byDebugger: Boolean = false) {
        val task = object : Task.Backgroundable(project, "Start Wokwi Simulator") {
            override fun run(indicator: ProgressIndicator) {
                startSimulatorInternal(byProcess, byDebugger)
            }
        }
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(
            task,
            BackgroundableProcessIndicator(task)
        )
    }

    private fun startSimulatorInternal(byProcess: WokwiProcessHandler? = null, byDebugger: Boolean = false) {
        LOG.info("Start simulator...")

        if (simulator == null || byDebugger)
            createNewSimulator(byDebugger)
        else
            updateFirmware()


        byProcess?.let { simulator?.addSimulatorListener(it) }
        simulator?.start()

        invokeLater {
            ToolWindowUtils.setSimulatorIcon(project, true)
            activateConsoleToolWindow()
        }
    }

    private fun createNewSimulator(waitForDebugger: Boolean = false) {
        val config =
            WokwiConfigProcessor.loadConfig(
                project,
                settingsState.wokwiConfigPath,
                settingsState.wokwiDiagramPath
            )
                ?: return
        val args = argsLoader.load(config) ?: return
        args.waitForDebugger = waitForDebugger

        simulator?.disposeByDisposer()
        val browser = SimulatorJCEFHtmlPanel()
        simulator = WokwiSimulator(args, browser)
        Disposer.register(this, simulator!!)

        invokeLater {
            val console = getConsole()
            simulator?.addSimulatorListener(console)

            componentService.simulatorToolWindowComponent.showSimulation(browser.component)
            componentService.consoleToolWindowComponent.setConsole(console)
        }
    }

    private fun updateFirmware() {
        simulator?.let {
            val firmware = it.getFirmware().rootFile
            val newFirmware = argsLoader.loadFirmware(firmware) ?: return
            it.setFirmware(newFirmware)
        }
    }

    fun stopSimulator() {
        simulator?.disposeByDisposer()
        simulator = null

        ToolWindowUtils.setSimulatorIcon(project, false)
        componentService.simulatorToolWindowComponent.showConfig()
    }


    override fun dispose() {

    }

    fun firmwareUpdated() {
        WokwiNotifier.notifyBalloon(title = "New firmware detected", "Restarting Wokwi simulator...")
        startSimulator()
    }

    fun getWatchPaths(): List<String>? {
        return simulator?.getFirmware()?.binaryPaths
    }

    fun isSimulatorRunning(): Boolean {
        return simulator != null
    }

    fun ableToStart(): Boolean {
        return licensingService.getLicense() != null &&
                Path(settingsState.wokwiConfigPath).resolveWith(project)?.exists() ?: false &&
                Path(settingsState.wokwiConfigPath).resolveWith(project)?.exists() ?: false
    }

    private fun getConsole(): SimulationConsole {
        val console = this.console ?: run {
            val c = SimulationConsole(project)
            Disposer.register(this, c)
            c
        }
        return console
    }

    private fun activateConsoleToolWindow() {
        consoleToolWindow?.let {
            it.show()
            return
        }
        consoleToolWindow =
            ToolWindowManager.getInstance(project)
                .registerToolWindow("Wokwi Run") {
                    val factory = ConsoleWindowFactory()
                    contentFactory = factory
                    icon = WokwiIcons.ConsoleToolWindowIcon
                    canCloseContent = false
                }
    }


    companion object {
        private val LOG = logger<WokwiProjectService>()
    }

}