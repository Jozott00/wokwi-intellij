package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.github.jozott00.wokwiintellij.simulator.gdb.WokwiGDBServer
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.github.jozott00.wokwiintellij.toolWindow.ConsoleWindowFactory
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.github.jozott00.wokwiintellij.utils.ToolWindowUtils
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.namedChildScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class WokwiProjectService(val project: Project, private val cs: CoroutineScope) : Disposable {

    private var simulator: WokwiSimulator? = null
    private var console: SimulationConsole? = null

    private val componentService = project.service<WokwiComponentService>()
    private val settingsState = project.service<WokwiSettingsState>()
    private val argsLoader = project.service<WokwiArgsLoader>()
    private var consoleToolWindow: ToolWindow? = null
    private var gdbServer: WokwiGDBServer? = null

    @Suppress("UnstableApiUsage")
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
            createNewSimulator(byDebugger)
        } else {
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

        if (!JBCefApp.isSupported()) {
            WokwiNotifier.notifyBalloonAsync(
                "Could not create Wokwi simulator",
                "JCEF browser is not supported. Please report this issue on the wokwi-intellij Github repository.",
                NotificationType.ERROR
            )
            return false
        }
        configGDBServer(
            waitForDebugger,
            config.gdbServerPort ?: 3333
        ) // configures gdbServer for new simulator instance

        simulator = WokwiSimulator(args, this).also {
            gdbServer?.let { server -> cs.launch { it.connectToGDBServer(server) } } // connect to server
        }

        withContext(Dispatchers.EDT) {
            val console = getConsole()
            simulator?.addSimulatorListener(console)

            simulator?.let { componentService.simulatorToolWindowComponent.showSimulation(it.component) }
            componentService.consoleToolWindowComponent.setConsole(console)
        }

        return true
    }

    private fun configGDBServer(shouldDebug: Boolean, port: Int) {
        if (!shouldDebug) {
            gdbServer?.disposeByDisposer()
            gdbServer = null
        } else {
            gdbServer?.let {
                if (!it.isRunning()) {
                    it.disposeByDisposer()
                    return@let
                }

                it.resetEventChannel()
                return
            }
            gdbServer = WokwiGDBServer(this.childScope("WokwiGDBServer"), this).also {
                it.listen(port)
            }
        }
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

        gdbServer?.disposeByDisposer()
        gdbServer = null

        withContext(Dispatchers.EDT) {
            ToolWindowUtils.setSimulatorIcon(project, false)
            componentService.simulatorToolWindowComponent.showConfig()
        }
    }


    override fun dispose() {
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