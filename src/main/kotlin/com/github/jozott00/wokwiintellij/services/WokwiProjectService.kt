package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.namedChildScope
import kotlinx.coroutines.*
import kotlin.io.path.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class WokwiProjectService(val project: Project, private val cs: CoroutineScope) : Disposable {

    private var simulator: WokwiSimulator? = null
    private var console: SimulationConsole? = null

    private val componentService = project.service<WokwiComponentService>()
    private val settingsState = project.service<WokwiSettingsState>()
    private val licensingService = ApplicationManager.getApplication().service<WokwiLicensingService>()
    private val argsLoader = project.service<WokwiArgsLoader>()
    private var consoleToolWindow: ToolWindow? = null

    fun childScope(name: String) = cs.namedChildScope(name)

    fun startSimulator(withListener: WokwiSimulatorListener? = null, byDebugger: Boolean = false) {
        cs.launch {
            startSimulatorSuspended(withListener, byDebugger)
        }
    }

    suspend fun startSimulatorSuspended(
        withListener: WokwiSimulatorListener? = null,
        byDebugger: Boolean = false
    ): Boolean {
        LOG.info("Start simulator...")

        if (simulator == null || byDebugger) {
            println("CREATE NEW SIMULATOR")

            createNewSimulator(byDebugger)

        } else {
            println("UPDATE FIRMWARE")
            updateFirmware()
        }.also { if (!it) return false }


        withListener?.let { simulator?.addSimulatorListener(it) }
        simulator?.start()

        invokeLater {
            ToolWindowUtils.setSimulatorIcon(project, true)
            activateConsoleToolWindow()
        }

        return true
    }

    private suspend fun createNewSimulator(waitForDebugger: Boolean = false): Boolean {

        val config =
            WokwiConfigProcessor.loadConfig(
                project,
                settingsState.wokwiConfigPath,
                settingsState.wokwiDiagramPath
            )
                ?: return false
        val args = argsLoader.load(config) ?: return false
        args.waitForDebugger = waitForDebugger

        simulator?.disposeByDisposer()
        val browser = SimulatorJCEFHtmlPanel()
        simulator = WokwiSimulator(args, browser)
        Disposer.register(this@WokwiProjectService, simulator!!)


        withContext(Dispatchers.EDT) {
            val console = getConsole()
            simulator?.addSimulatorListener(console)

            componentService.simulatorToolWindowComponent.showSimulation(browser.component)
            componentService.consoleToolWindowComponent.setConsole(console)
        }

        return true
    }

    private suspend fun updateFirmware(): Boolean {
        simulator?.let {
            val firmware = it.getFirmware().rootFile
            val newFirmware = argsLoader.loadFirmware(firmware) ?: return false
            it.setFirmware(newFirmware)
        }

        return true
    }

    fun stopSimulator() = cs.launch {
        simulator?.disposeByDisposer()
        simulator = null

        withContext(Dispatchers.EDT) {
            ToolWindowUtils.setSimulatorIcon(project, false)
            componentService.simulatorToolWindowComponent.showConfig()
        }
    }


    override fun dispose() {
        println("DISPOSING WokwiProjectService")
    }

    fun firmwareUpdated() = cs.launch {
        WokwiNotifier.notifyBalloonAsync(title = "New firmware detected", "Restarting Wokwi simulator...")
        startSimulatorSuspended()
    }

    fun getWatchPaths(): List<String>? {
        return simulator?.getFirmware()?.binaryPaths
    }

    fun isSimulatorRunning(): Boolean {
        return simulator != null
    }

    suspend fun ableToStart(): Boolean {
        return licensingService.getLicense() != null &&
                Path(settingsState.wokwiConfigPath).resolveWith(project)?.exists() ?: false &&
                Path(settingsState.wokwiConfigPath).resolveWith(project)?.exists() ?: false
    }

    private suspend fun getConsole(): SimulationConsole {
        return withContext(Dispatchers.EDT) {
            val console = this@WokwiProjectService.console ?: run {
                val c = SimulationConsole(project)
                Disposer.register(this@WokwiProjectService, c)
                c
            }
            console
        }
    }

    private fun activateConsoleToolWindow() = cs.launch(Dispatchers.EDT) {
        consoleToolWindow?.let {
            it.show()
            return@launch
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