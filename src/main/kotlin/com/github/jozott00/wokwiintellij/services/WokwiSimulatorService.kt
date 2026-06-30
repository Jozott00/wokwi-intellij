package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.execution.processHandler.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.execution.processHandler.WokwiRunProcessHandler
import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.extensions.wokwiDisposable
import com.github.jozott00.wokwiintellij.core.protocol.InboundDecodeResult
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.core.session.WokwiSessionStartConfig
import com.github.jozott00.wokwiintellij.simulator.SimExitCode
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.github.jozott00.wokwiintellij.simulator.gdb.WokwiSessionGdbBridge
import com.github.jozott00.wokwiintellij.simulator.gdb.WokwiGDBServer
import com.github.jozott00.wokwiintellij.simulator.services.UrlWokwiResourceLoader
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.github.jozott00.wokwiintellij.ui.jcef.JcefWokwiView
import com.github.jozott00.wokwiintellij.utils.ToolWindowUtils
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.containers.ContainerUtil
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

    private var currentView: JcefWokwiView? = null
    private var currentSession: WokwiSession? = null
    private var currentArgs: WokwiArgs? = null
    private var currentProcessHandler: WokwiProcessHandler? = null
    private var gdbBridge: WokwiSessionGdbBridge? = null

    private val componentService by lazy { project.service<WokwiComponentService>() }
    private val settingsState by lazy { project.service<WokwiSettingsState>() }
    private val argsLoader by lazy { project.service<WokwiArgsLoader>() }
    private val resourceLoader = UrlWokwiResourceLoader()
    private val ansiEscapeDecoder = AnsiEscapeDecoder()
    private val simulatorListeners: MutableList<WokwiSimulatorListener> = ContainerUtil.createLockFreeCopyOnWriteList()
    private var gdbServer: WokwiGDBServer? = null

    /**
     * Creates a new coroutine scope as a child of the service's main coroutine scope.
     *
     * @return A new [CoroutineScope] instance.
     */
    // TODO: implement this using cs.namedChildScope() once it is stable
    fun childScope() = CoroutineScope(cs.coroutineContext + SupervisorJob(cs.coroutineContext[Job]))

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
                processHandler.onShutdown(SimExitCode.CONFIG_ERROR)
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

        if (currentSession == null || byDebugger) {
            if (!createNewSimulator(byDebugger)) return false
        } else {
            if (!updateFirmware()) return false
        }

        listener?.let { addSimulatorListener(it) }
        currentSession?.start()

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

        disposeCurrentSimulator(clearListeners = true)

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

        val view = JcefWokwiView()
        Disposer.register(this, view)

        val session = WokwiSession(
            transport = view.wokwiTransport,
            initialConfig = args.toSessionStartConfig(),
            resourceLoader = resourceLoader,
            listener = createSessionListener(),
        )

        currentView = view
        currentSession = session
        currentArgs = args

        gdbBridge = gdbServer?.let { server ->
            WokwiSessionGdbBridge(session, server).also { bridge ->
                childScope().launch { bridge.connect() }
            }
        }

        withContext(Dispatchers.EDT) {
            componentService.simulatorToolWindowComponent.showSimulation(view.component)
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
            gdbServer = WokwiGDBServer(childScope(), project.wokwiDisposable).also {
                it.listen(port)
            }
        }
    }

    /**
     * Updates the firmware of the currently running simulator.
     *
     * @return `true` if the firmware was successfully updated, `false` otherwise.
     */
    private suspend fun updateFirmware(): Boolean = currentArgs?.let {
        val firmware = it.firmware.rootFile
        val newFirmware = argsLoader.loadFirmware(firmware) ?: return false
        it.firmware = newFirmware
        currentSession?.updateStartConfig(it.toSessionStartConfig())
        true
    } ?: false


    /**
     * Stops the currently running Wokwi simulator and cleans up resources.
     */
    fun stopSimulator() = cs.launch {
        LOG.info("Stop simulator...")

        disposeCurrentSimulator(clearListeners = true)

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

    override fun dispose() {
        disposeCurrentSimulator(clearListeners = true)
    }

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
    fun getWatchPaths(): List<String>? = currentArgs?.firmware?.binaryPaths

    /**
     * Checks whether the Wokwi simulator is currently running.
     *
     * @return `true` if the simulator is running, `false` otherwise.
     */
    fun isSimulatorRunning(): Boolean = currentSession != null

    private fun addSimulatorListener(listener: WokwiSimulatorListener) {
        simulatorListeners.add(listener)
    }

    private fun disposeCurrentSimulator(clearListeners: Boolean) {
        if (currentSession != null) {
            notifySimulatorListeners { it.onShutdown(SimExitCode.OK) }
        }

        currentSession?.dispose()
        currentSession = null

        currentView?.disposeByDisposer()
        currentView = null

        currentArgs = null
        gdbBridge = null

        if (clearListeners) {
            simulatorListeners.clear()
        }
    }

    private fun createSessionListener(): WokwiSession.Listener {
        return object : WokwiSession.Listener {
            override fun onStarted(config: WokwiSessionStartConfig) {
                LOG.info("(Re)starting simulation...")
                currentArgs?.let { notifySimulatorListeners { listener -> listener.onStarted(it) } }
            }

            override fun onRunning() {
                notifySimulatorListeners { it.onRunning() }
            }

            override fun onUartData(bytes: ByteArray) {
                if (bytes.isEmpty()) return

                val text = String(bytes, Charsets.UTF_8)
                ansiEscapeDecoder.escapeText(text, ProcessOutputTypes.STDOUT) { decodedText, contentType ->
                    notifySimulatorListeners { it.onTextAvailable(decodedText, contentType) }
                }
            }

            override fun onGdbResponse(response: String) {
                gdbBridge?.sendResponse(response)
            }

            override fun onMalformedMessage(message: InboundDecodeResult.Malformed) {
                LOG.error("Malformed Wokwi message: ${message.reason}\n${message.raw}", Throwable())
            }

            override fun onUnknownMessage(message: InboundMessage.Unknown) {
                LOG.warn("Unknown command: ${message.command}")
                LOG.debug("Unknown command data: ${message.raw}")
            }

            override fun onUnsupportedMessage(message: InboundMessage) {
                LOG.warn("Unsupported Wokwi command: ${message.command}")
            }
        }
    }

    private fun notifySimulatorListeners(event: (WokwiSimulatorListener) -> Unit) {
        for (listener in simulatorListeners) {
            event(listener)
        }
    }

    private fun WokwiArgs.toSessionStartConfig() = WokwiSessionStartConfig(
        license = license,
        diagram = diagram,
        firmware = firmware.buffer,
        firmwareFormat = firmware.format.toString(),
        waitForDebugger = waitForDebugger,
    )


    companion object {
        private val LOG = logger<WokwiSimulatorService>()
    }

}
