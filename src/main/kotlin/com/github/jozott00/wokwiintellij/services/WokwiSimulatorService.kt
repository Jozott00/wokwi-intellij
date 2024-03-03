package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.execution.processHandler.WokwiProcessHandler
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
import kotlinx.coroutines.*

/**
 * Manages the lifecycle and configuration of the Wokwi simulator within the project context.
 * It handles starting, stopping, and updating the Wokwi simulator and its components.
 *
 * @property project The IntelliJ [Project] context in which this service operates.
 * @property cs The [CoroutineScope] used for launching coroutines in this service.
 */
@Service(Service.Level.PROJECT)
class WokwiSimulatorService(val project: Project, private val cs: CoroutineScope) : Disposable {

    private var simulator: WokwiSimulator? = null
    private var currentProcessHandler: WokwiProcessHandler? = null

    private val componentService by lazy { project.service<WokwiComponentService>() }
    private val settingsState by lazy { project.service<WokwiSettingsState>() }
    private val argsLoader by lazy { project.service<WokwiArgsLoader>() }
    private var gdbServer: WokwiGDBServer? = null

    /**
     * Creates a new coroutine scope as a child of the service's main coroutine scope.
     *
     * @return A new [CoroutineScope] instance.
     */
    // TODO: implement this using cs.namedChildScope() once it is stable
    fun childScope() = CoroutineScope(cs.coroutineContext + SupervisorJob())

    /**
     * Starts the Wokwi simulator with optional debugger support.
     * This function creates a new process handler for the simulator if needed and launches
     * the simulator asynchronously.
     *
     * @param byDebugger Indicates whether the simulator is started with debugger support.
     * @return The [WokwiProcessHandler] associated with the simulator process.
     */
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

    /**
     * Asynchronously starts the Wokwi simulator, with the option to attach a debugger.
     * This function can either create a new simulator instance or update the firmware of an existing one.
     *
     * @param listener An optional [WokwiSimulatorListener] to be notified about simulator events.
     * @param byDebugger Indicates whether the simulator is started with debugger support.
     * @return `true` if the simulator was successfully started, `false` otherwise.
     */
    suspend fun startSimulatorAsync(
        listener: WokwiSimulatorListener? = null,
        byDebugger: Boolean = false
    ): Boolean {
        LOG.info("Start simulator...")

        if (simulator == null || byDebugger) {
            if (!createNewSimulator(byDebugger)) return false
        } else {
            if (!updateFirmware()) return false
        }

        listener?.let { simulator?.addSimulatorListener(it) }
        simulator?.start()

        return true
    }

    /**
     * Creates a new instance of the Wokwi simulator.
     * This function loads the simulator configuration, initializes a new simulator instance, and optionally configures GDB server.
     *
     * @param waitForDebugger Indicates whether to wait for a debugger connection.
     * @return `true` if the simulator was successfully created, `false` otherwise.
     */
    private suspend fun createNewSimulator(waitForDebugger: Boolean = false): Boolean {

        val config =
            WokwiConfigProcessor.loadConfig(
                project,
                settingsState.wokwiConfigPath,
                settingsState.wokwiDiagramPath
            ) ?: return false

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
            gdbServer?.let { server -> childScope().launch { it.connectToGDBServer(server) } } // connect to server
        }

        withContext(Dispatchers.EDT) {
            simulator?.let { componentService.simulatorToolWindowComponent.showSimulation(it.component) }
            ToolWindowUtils.setSimulatorIcon(project, true)
        }

        return true
    }

    /**
     * Retrieves the current [WokwiProcessHandler] for the simulator, creating a new one if necessary.
     *
     * @return The current or a new [WokwiProcessHandler].
     */
    private fun getProcessHandler(): WokwiProcessHandler =
        currentProcessHandler.takeIf { it?.isProcessTerminated == false }
            ?: WokwiRunProcessHandler(project).also { currentProcessHandler = it }


    /**
     * Configures the GDB server for debugging the simulator.
     *
     * @param shouldDebug Indicates whether debugging is enabled.
     * @param port The port number on which the GDB server should listen.
     */
    private fun configGDBServer(shouldDebug: Boolean, port: Int?) {
        gdbServer?.apply {
            if (!shouldDebug || !isRunning()) {
                disposeByDisposer()
                gdbServer = null
            } else {
                resetEventChannel()
            }
        }

        if (shouldDebug && gdbServer == null) {
            gdbServer = WokwiGDBServer(childScope(), this).also {
                it.listen(port)
            }
        }
    }

    /**
     * Updates the firmware of the currently running simulator.
     *
     * @return `true` if the firmware was successfully updated, `false` otherwise.
     */
    private suspend fun updateFirmware(): Boolean = simulator?.let {
        val firmware = it.getFirmware().rootFile
        val newFirmware = argsLoader.loadFirmware(firmware) ?: return false
        it.setFirmware(newFirmware)
        true
    } ?: false


    /**
     * Stops the currently running Wokwi simulator and cleans up resources.
     */
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

    /**
     * Gets the current GDB server port if the server is running.
     *
     * @return The GDB server port, or `null` if the server is not running.
     */
    fun getRunningGDBPort(): Int? = gdbServer?.getCurrentServerPort()

    override fun dispose() {}

    /**
     * Notifies the service that the firmware has been updated and restarts the simulator.
     */
    fun firmwareUpdated() = cs.launch {
        WokwiNotifier.notifyBalloonAsync(title = "New firmware detected", "Restarting Wokwi simulator...")
        startSimulatorAsync()
    }

    /**
     * Gets the watch paths for the simulator's firmware.
     *
     * @return A list of firmware binary paths, or `null` if the simulator is not running.
     */
    fun getWatchPaths(): List<String>? = simulator?.getFirmware()?.binaryPaths

    /**
     * Checks whether the Wokwi simulator is currently running.
     *
     * @return `true` if the simulator is running, `false` otherwise.
     */
    fun isSimulatorRunning(): Boolean = simulator != null


    companion object {
        private val LOG = logger<WokwiSimulatorService>()
    }

}