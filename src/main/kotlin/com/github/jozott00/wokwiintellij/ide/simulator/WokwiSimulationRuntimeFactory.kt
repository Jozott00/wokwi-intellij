package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.model.SimulationConfig
import com.github.jozott00.wokwiintellij.core.ports.ResourceLoader
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.core.session.WokwiSessionStartConfig
import com.github.jozott00.wokwiintellij.services.LoadedSimulationConfig
import com.github.jozott00.wokwiintellij.services.SimulationConfigLoader
import com.github.jozott00.wokwiintellij.simulator.services.DefaultGdbServer
import com.github.jozott00.wokwiintellij.ui.jcef.JcefWokwiView
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.CoroutineScope

/**
 * Creates active simulator runtime objects from loaded project configuration.
 *
 * This factory keeps JCEF-specific construction and start-config mapping out of [WokwiSessionController]. It still
 * lives in the IntelliJ adapter layer because it creates [JcefWokwiView] instances and checks platform browser support.
 *
 * @property owner disposable parent for created browser views.
 * @property childScope scope factory used by each created [WokwiSession].
 * @property simulationConfigLoader project-aware configuration and firmware loader.
 * @property resourceLoader adapter used by sessions to fetch Wokwi resources.
 */
class WokwiSimulationRuntimeFactory(
    private val owner: WokwiSessionController,
    private val childScope: () -> CoroutineScope,
    private val simulationConfigLoader: SimulationConfigLoader,
    private val resourceLoader: ResourceLoader,
) {
    /**
     * Loads project simulation configuration for a normal or debugger-backed start.
     */
    suspend fun loadConfig(waitForDebugger: Boolean): LoadedSimulationConfig? =
        simulationConfigLoader.load(waitForDebugger)

    /**
     * Reloads firmware for an existing runtime while preserving the rest of its simulation config.
     */
    suspend fun loadFirmware(config: SimulationConfig) =
        simulationConfigLoader.loadFirmware(config.firmware.rootPath)

    /**
     * Checks whether JCEF is available and reports a user-visible error when it is not.
     */
    suspend fun ensureBrowserSupported(): Boolean {
        if (JBCefApp.isSupported()) return true

        WokwiNotifier.notifyBalloonAsync(
            "Could not create Wokwi simulator",
            "JCEF browser is not supported. Please report this issue on the wokwi-intellij Github repository.",
            NotificationType.ERROR
        )
        return false
    }

    /**
     * Creates the JCEF view and core session objects for one active simulation runtime.
     *
     * The returned runtime owns the session and view pair; disposing it tears both down.
     */
    fun createRuntime(
        simulationConfig: SimulationConfig,
        gdbServer: DefaultGdbServer?,
        listener: WokwiSession.Listener,
    ): WokwiSimulationRuntime {
        val view = JcefWokwiView()
        Disposer.register(owner, view)

        val session = WokwiSession(
            coroutineScope = childScope(),
            transport = view.wokwiTransport,
            initialConfig = createStartConfig(simulationConfig, gdbServer?.getCurrentServerPort()),
            resourceLoader = resourceLoader,
            gdbServer = gdbServer,
            listener = listener,
        )

        return WokwiSimulationRuntime(
            view = view,
            session = session,
            simulationConfig = simulationConfig,
        )
    }

    /**
     * Maps the IDE-facing simulation config into the pure session start payload.
     */
    fun createStartConfig(config: SimulationConfig, gdbPort: Int?): WokwiSessionStartConfig =
        WokwiSessionStartConfig(
            license = config.license,
            diagram = config.diagram,
            firmware = config.firmware.buffer,
            firmwareFormat = config.firmware.format.toString(),
            waitForDebugger = config.waitForDebugger,
            gdbPort = gdbPort,
        )
}
