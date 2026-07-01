package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.execution.processHandler.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.execution.processHandler.WokwiRunProcessHandler
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.services.SimulationConfigLoader
import com.github.jozott00.wokwiintellij.services.WokwiComponentService
import com.github.jozott00.wokwiintellij.simulator.services.UrlWokwiResourceLoader
import com.github.jozott00.wokwiintellij.utils.ToolWindowUtils
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Project-level owner of the active Wokwi simulator lifecycle.
 *
 * This is the IntelliJ-facing adapter around the pure [com.github.jozott00.wokwiintellij.core.session.WokwiSession].
 * It coordinates configuration loading, JCEF view creation, process handler state, tool window updates, firmware
 * reloads, and debugger integration for one project.
 *
 * @property project IntelliJ project that owns the simulator session.
 * @property cs parent coroutine scope injected by the IntelliJ service container.
 */
@Service(Service.Level.PROJECT)
class WokwiSessionController(val project: Project, private val cs: CoroutineScope) : Disposable {
    private var currentRuntime: WokwiSimulationRuntime? = null
    private var currentProcessHandler: WokwiProcessHandler? = null

    private val componentService by lazy { project.service<WokwiComponentService>() }
    private val simulationConfigLoader by lazy { project.service<SimulationConfigLoader>() }
    private val gdbServerManager = WokwiGdbServerManager(project, ::childScope)
    private val eventDispatcher = WokwiSessionEventDispatcher()
    private val runtimeFactory by lazy {
        WokwiSimulationRuntimeFactory(
            owner = this,
            childScope = ::childScope,
            simulationConfigLoader = simulationConfigLoader,
            resourceLoader = UrlWokwiResourceLoader(),
        )
    }

    init {
        eventDispatcher.subscribePersistent(WokwiSessionDiagnosticsListener(LOG))
    }

    /**
     * Creates a supervised child scope tied to the project service scope.
     *
     * Long-running simulator helpers use child scopes so they can be cancelled independently without cancelling the
     * service-level scope shared by IntelliJ.
     */
    // TODO: implement this using cs.namedChildScope() once it is stable
    fun childScope() = CoroutineScope(cs.coroutineContext + SupervisorJob(cs.coroutineContext[Job]))

    /**
     * Starts the simulator from run/action code and returns the process handler immediately.
     *
     * Startup continues asynchronously. If configuration loading or runtime creation fails, the returned process
     * handler is completed with [SimExitCode.CONFIG_ERROR].
     *
     * @param byDebugger whether the simulator should be started with GDB support enabled.
     */
    fun startSimulator(byDebugger: Boolean = false): WokwiProcessHandler {
        val processHandler = getProcessHandler()
        cs.launch {
            val result = startSimulatorAsync(processHandler, byDebugger)
            if (!result) {
                processHandler.onShutdown(SimExitCode.CONFIG_ERROR)
            }
        }
        return processHandler
    }

    /**
     * Starts or restarts the simulator and optionally attaches a one-shot listener caller.
     *
     * When no runtime exists, or when debugger support is requested, a fresh runtime is created. Otherwise the existing
     * session receives an updated firmware start config and is started again.
     *
     * @param listener optional session listener to receive events for this run.
     * @param byDebugger whether a GDB server should be available to Wokwi.
     * @return `true` when startup data was prepared and the session was asked to start.
     */
    suspend fun startSimulatorAsync(
        listener: WokwiSession.Listener? = null,
        byDebugger: Boolean = false,
    ): Boolean {
        LOG.info("Start simulator...")

        if (currentRuntime == null || byDebugger) {
            if (!createNewSimulator(byDebugger)) return false
        } else {
            if (!updateFirmware()) return false
        }

        listener?.let { eventDispatcher.subscribe(it) }
        currentRuntime?.session?.start()

        return true
    }

    /**
     * Stops the active simulator runtime and restores the tool window to the configuration view.
     *
     * The stop work is launched asynchronously because action and process handler callers are synchronous IntelliJ APIs.
     */
    fun stopSimulator() = cs.launch {
        LOG.info("Stop simulator...")

        disposeCurrentRuntime(clearListeners = true)

        gdbServerManager.disposeServer()

        currentProcessHandler?.destroyProcess()
        currentProcessHandler = null

        withContext(Dispatchers.EDT) {
            ToolWindowUtils.setSimulatorIcon(project, false)
            componentService.simulatorToolWindowComponent.showConfig()
        }
    }

    /**
     * Returns the bound GDB server port for the current debug session, if one is running.
     */
    fun getRunningGDBPort(): Int? = gdbServerManager.runningPort()

    /**
     * Handles a watched firmware change by notifying the user and restarting the current simulator session.
     */
    fun firmwareUpdated() = cs.launch {
        WokwiNotifier.notifyBalloonAsync(title = "New firmware detected", "Restarting Wokwi simulator...")
        startSimulatorAsync()
    }

    /**
     * Returns firmware files watched for reload while a simulator runtime is active.
     */
    fun getWatchPaths(): List<Path>? = currentRuntime?.simulationConfig?.firmware?.watchPaths

    /**
     * Indicates whether this project currently owns an active simulator runtime.
     */
    fun isSimulatorRunning(): Boolean = currentRuntime != null

    override fun dispose() {
        disposeCurrentRuntime(clearListeners = true)
        gdbServerManager.disposeServer()
    }

    private suspend fun createNewSimulator(waitForDebugger: Boolean = false): Boolean {
        val loadedConfig = runtimeFactory.loadConfig(waitForDebugger) ?: return false
        val simulationConfig = loadedConfig.simulationConfig

        disposeCurrentRuntime(clearListeners = true)

        if (!runtimeFactory.ensureBrowserSupported()) return false

        val gdbServer = gdbServerManager.configure(
            shouldDebug = waitForDebugger,
            port = loadedConfig.gdbServerPort,
        )

        val runtime = runtimeFactory.createRuntime(
            simulationConfig = simulationConfig,
            gdbServer = gdbServer,
            listener = eventDispatcher.asSessionListener(),
        )

        currentRuntime = runtime

        withContext(Dispatchers.EDT) {
            componentService.simulatorToolWindowComponent.showSimulation(runtime.view.component)
            ToolWindowUtils.setSimulatorIcon(project, true)
        }

        return true
    }

    private fun getProcessHandler(): WokwiProcessHandler =
        currentProcessHandler.takeIf { it?.isProcessTerminated == false }
            ?: WokwiRunProcessHandler(project).also { currentProcessHandler = it }

    private suspend fun updateFirmware(): Boolean {
        val runtime = currentRuntime ?: return false
        val newFirmware = runtimeFactory.loadFirmware(runtime.simulationConfig) ?: return false
        val updatedSimulationConfig = runtime.simulationConfig.copy(firmware = newFirmware)

        runtime.simulationConfig = updatedSimulationConfig
        runtime.session.updateStartConfig(
            runtimeFactory.createStartConfig(updatedSimulationConfig, gdbServerManager.runningPort())
        )

        return true
    }

    private fun disposeCurrentRuntime(clearListeners: Boolean) {
        if (currentRuntime != null) {
            currentProcessHandler?.onShutdown(SimExitCode.OK)
        }

        currentRuntime?.dispose()
        currentRuntime = null

        if (clearListeners) {
            eventDispatcher.clearSessionSubscribers()
        }
    }

    companion object {
        private val LOG = logger<WokwiSessionController>()
    }
}
