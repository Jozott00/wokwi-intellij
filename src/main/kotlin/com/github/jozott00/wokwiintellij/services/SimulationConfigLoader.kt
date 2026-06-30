package com.github.jozott00.wokwiintellij.services

import arrow.core.Either
import com.github.jozott00.wokwiintellij.config.WokwiProjectConfigResolver
import com.github.jozott00.wokwiintellij.core.model.FirmwareImage
import com.github.jozott00.wokwiintellij.core.model.SimulationConfig
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier.notifyBalloonAsync
import com.github.jozott00.wokwiintellij.utils.simulation.FirmwareUtils
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fully loaded simulator startup data.
 *
 * [simulationConfig] is the runtime payload passed toward the browser/session layer. [gdbServerPort] is kept separate
 * because it configures the local IntelliJ-side GDB server and is not part of the browser startup payload.
 */
data class LoadedSimulationConfig(
    val simulationConfig: SimulationConfig,
    val gdbServerPort: Int?,
)

/**
 * Project-level loader that turns Wokwi project configuration into runtime simulation data.
 *
 * This service is intentionally outside `core`: it reads IntelliJ project settings, resolves project files, checks the
 * Wokwi license, reports user-facing load errors, and loads firmware bytes. The resulting [SimulationConfig] is pure
 * data that can be passed to the simulator session.
 */
@Service(Service.Level.PROJECT)
class SimulationConfigLoader(val project: Project) {

    private var licensingService = ApplicationManager.getApplication().service<WokwiLicensingService>()
    private val settingsState by lazy { project.service<WokwiSettingsState>() }
    private val configResolver = WokwiProjectConfigResolver(project)

    /**
     * Loads all data required to create a new simulator session.
     *
     * Returns `null` after reporting a user-facing error when configuration resolution, license validation, diagram
     * loading, or firmware loading fails.
     *
     * @param waitForDebugger whether the simulator should pause at startup until a debugger connects.
     */
    suspend fun load(waitForDebugger: Boolean): LoadedSimulationConfig? {
        val config = configResolver.resolve(
            settingsState.wokwiConfigPath,
            settingsState.wokwiDiagramPath
        ) ?: return null

        val license = loadLicense() ?: return null
        val diagram = withContext(Dispatchers.IO) { Files.readString(config.diagramPath) }
        val firmware = loadFirmware(config.firmwarePath) ?: return null

        return LoadedSimulationConfig(
            simulationConfig = SimulationConfig(
                license = license,
                diagram = diagram,
                firmware = firmware,
                waitForDebugger = waitForDebugger,
            ),
            gdbServerPort = config.gdbServerPort,
        )
    }

    /**
     * Loads and classifies the firmware image at [firmwarePath].
     *
     * For regular firmware files this reads the file bytes directly. For ESP-IDF `flasher_args.json` files it delegates
     * to [FirmwareUtils] to pack the referenced images and returns all watched paths needed for automatic reloads.
     *
     * Returns `null` after reporting a user-facing error when the firmware cannot be read or packed.
     */
    suspend fun loadFirmware(firmwarePath: Path): FirmwareImage? = withContext(Dispatchers.IO) {
        val firmwareFile = firmwarePath.normalize()
        if (!Files.exists(firmwareFile)) {
            withContext(Dispatchers.EDT) {
                notifyBalloonAsync(
                    title = "Failed to load firmware",
                    message = "Firmware `$firmwarePath` does not exist and therefore cannot be loaded for simulation.",
                    NotificationType.ERROR
                )
            }
            return@withContext null
        }

        val isFlasherArgsFile = firmwareFile.fileName.toString() == "flasher_args.json"
        val watchPaths = mutableListOf(firmwareFile)

        val buffer = if (isFlasherArgsFile) {
            val packedResult =
                when (val result = FirmwareUtils.packEspIdfFirmware(firmwareFile)) {
                    is Either.Left -> {
                        notifyBalloonAsync(result.value)
                        return@withContext null
                    }

                    is Either.Right -> result.value
                }

            watchPaths.addAll(packedResult.watchPaths)
            packedResult.img
        } else {
            Files.readAllBytes(firmwareFile)
        }

        val format = FirmwareUtils.determineFirmwareFormat(firmwareFile, buffer)

        FirmwareImage(
            buffer = buffer,
            format = format,
            rootPath = firmwareFile,
            isFlasherFile = isFlasherArgsFile,
            size = buffer.size.toUInt(),
            watchPaths = watchPaths
        )
    }

    private suspend fun loadLicense() = licensingService.loadAndCheckLicense()
        .onLeft {
            notifyBalloonAsync(
                title = it.title,
                message = it.message,
                type = NotificationType.ERROR
            )
        }.getOrNull()

}
