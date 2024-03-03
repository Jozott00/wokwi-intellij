package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.execution.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.execution.processHandler.WokwiRunProcessHandler
import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.simulator.EXIT_CODE
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.github.jozott00.wokwiintellij.simulator.gdb.WokwiGDBServer
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
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
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class WokwiProjectService(val project: Project, private val cs: CoroutineScope) : Disposable {

    private var simulator: WokwiSimulator? = null
    private var currentProcessHandler: WokwiProcessHandler? = null

    private val componentService = project.service<WokwiComponentService>()
    private val settingsState = project.service<WokwiSettingsState>()
    private val argsLoader = project.service<WokwiArgsLoader>()
    private var gdbServer: WokwiGDBServer? = null

    // TODO: implement this using cs.namedChildScope() once it is stable
    fun childScope() = cs

    fun startSimulator(byDebugger: Boolean = false): WokwiProcessHandler {
        val processHandler = getProcessHandler()
        cs.launch {
            val result = startSimulatorAsync(processHandler, byDebugger)
            if (!result) {
                processHandler.onShutdown(EXIT_CODE.CONFIG_ERROR)
            }
        }
        return processHandler
    }

    suspend fun startSimulatorAsync(
        listener: WokwiSimulatorListener? = null,
        byDebugger: Boolean = false
    ): Boolean {
        LOG.info("Start simulator...")

        if (simulator == null || byDebugger) {
            createNewSimulator(byDebugger)
        } else {
            updateFirmware()
        }.also { if (!it) return false }

        listener?.let { simulator?.addSimulatorListener(it) }
        simulator?.start()

        invokeLater { ToolWindowUtils.setSimulatorIcon(project, true) }
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
            config.gdbServerPort
        ) // configures gdbServer for new simulator instance

        simulator = WokwiSimulator(args, this).also {
            gdbServer?.let { server -> cs.launch { it.connectToGDBServer(server) } } // connect to server
        }

        withContext(Dispatchers.EDT) {
            simulator?.let { componentService.simulatorToolWindowComponent.showSimulation(it.component) }
        }

        return true
    }

    private fun getProcessHandler(): WokwiProcessHandler {
        val current = currentProcessHandler
        val processHandler = if (current != null && !current.isProcessTerminated) {
            current
        } else {
            WokwiRunProcessHandler(project).also {
                currentProcessHandler = it
            }
        }

        return processHandler
    }


    private fun configGDBServer(shouldDebug: Boolean, port: Int?) {
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
            gdbServer = WokwiGDBServer(this.childScope(), this).also {
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
        LOG.info("Stop simulator...")

        simulator?.disposeByDisposer()
        simulator = null

        gdbServer?.disposeByDisposer()
        gdbServer = null

        currentProcessHandler?.destroyProcess()
        currentProcessHandler = null

        withContext(Dispatchers.EDT) {
            ToolWindowUtils.setSimulatorIcon(project, false)
            componentService.simulatorToolWindowComponent.showConfig()
        }
    }

    fun getRunningGDBPort(): Int? = gdbServer?.getCurrentServerPort()

    override fun dispose() {
    }

    fun firmwareUpdated() = cs.launch {
        WokwiNotifier.notifyBalloonAsync(title = "New firmware detected", "Restarting Wokwi simulator...")
        startSimulatorAsync()
    }

    fun getWatchPaths(): List<String>? {
        return simulator?.getFirmware()?.binaryPaths
    }

    fun isSimulatorRunning(): Boolean {
        return simulator != null
    }


    companion object {
        private val LOG = logger<WokwiProjectService>()
    }

}