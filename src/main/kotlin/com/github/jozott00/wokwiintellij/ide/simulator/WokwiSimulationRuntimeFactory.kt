package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.model.FirmwareImage
import com.github.jozott00.wokwiintellij.core.model.SimulationConfig
import com.github.jozott00.wokwiintellij.core.ports.GdbServer
import com.github.jozott00.wokwiintellij.core.ports.ResourceLoader
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.core.session.WokwiSessionStartConfig
import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.ide.services.IntelliJUserNotifier
import com.github.jozott00.wokwiintellij.ide.services.LoadedSimulationConfig
import com.github.jozott00.wokwiintellij.ide.services.SimulationConfigLoader
import com.github.jozott00.wokwiintellij.services.UserNotifier
import com.github.jozott00.wokwiintellij.ui.jcef.JcefWokwiView
import com.github.jozott00.wokwiintellij.ui.jcef.WokwiHtmlPageFactory
import com.intellij.openapi.application.EDT
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The lifecycle controller's configuration and browser construction boundary. */
interface SimulationRuntimeFactory {
    suspend fun loadConfig(waitForDebugger: Boolean): LoadedSimulationConfig?
    suspend fun loadFirmware(config: SimulationConfig): FirmwareImage?
    suspend fun ensureBrowserSupported(): Boolean
    suspend fun createRuntime(
        config: LoadedSimulationConfig,
        gdbServer: GdbServer?,
        listener: WokwiSession.Listener,
    ): SimulationRuntime

    fun createStartConfig(config: SimulationConfig, gdbPort: Int?) = WokwiSessionStartConfig(
        license = config.license,
        diagram = config.diagram,
        firmware = config.firmware.buffer,
        firmwareFormat = config.firmware.format.toString(),
        waitForDebugger = config.waitForDebugger,
        gdbPort = gdbPort,
        customChips = config.customChips,
    )
}

/** Creates Swing/JCEF objects on the EDT and supplies the service scope to session-owned jobs. */
class WokwiSimulationRuntimeFactory(
    private val coroutineScope: CoroutineScope,
    private val simulationConfigLoader: SimulationConfigLoader,
    private val resourceLoader: ResourceLoader,
    private val userNotifier: UserNotifier = IntelliJUserNotifier,
) : SimulationRuntimeFactory {
    override suspend fun loadConfig(waitForDebugger: Boolean) = simulationConfigLoader.load(waitForDebugger)

    override suspend fun loadFirmware(config: SimulationConfig) =
        simulationConfigLoader.loadFirmware(config.firmware.rootPath)

    override suspend fun ensureBrowserSupported(): Boolean {
        if (JBCefApp.isSupported()) return true
        userNotifier.error("Could not create Wokwi simulator", "JCEF browser is not supported.")
        return false
    }

    override suspend fun createRuntime(
        config: LoadedSimulationConfig,
        gdbServer: GdbServer?,
        listener: WokwiSession.Listener,
    ): SimulationRuntime {
        var created: WokwiSimulationRuntime? = null
        try {
            return withContext(Dispatchers.EDT) {
                val view = JcefWokwiView(
                    htmlOptions = WokwiHtmlPageFactory.Options(licenseUserId = config.licenseUserId)
                )
                try {
                    WokwiSimulationRuntime(
                        view = view,
                        session = WokwiSession(
                            coroutineScope = coroutineScope,
                            transport = view.wokwiTransport,
                            initialConfig = createStartConfig(config.simulationConfig, config.gdbServerPort),
                            resourceLoader = resourceLoader,
                            gdbServer = gdbServer,
                            listener = listener,
                        ),
                        simulationConfig = config.simulationConfig,
                    ).also { created = it }
                } catch (error: Throwable) {
                    view.disposeByDisposer()
                    throw error
                }
            }
        } catch (error: Throwable) {
            // withContext can discard its result if cancellation arrives while returning from the EDT.
            created?.dispose()
            throw error
        }
    }
}
