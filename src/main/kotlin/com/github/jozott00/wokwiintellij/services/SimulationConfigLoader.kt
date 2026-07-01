package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.config.WokwiProjectConfigResolver
import com.github.jozott00.wokwiintellij.core.config.WokwiResolvedCustomChip
import com.github.jozott00.wokwiintellij.core.firmware.EspIdfFirmwarePackager
import com.github.jozott00.wokwiintellij.core.firmware.FirmwareFormatDetector
import com.github.jozott00.wokwiintellij.core.firmware.FirmwarePackResult
import com.github.jozott00.wokwiintellij.core.model.CustomChip
import com.github.jozott00.wokwiintellij.core.model.FirmwareImage
import com.github.jozott00.wokwiintellij.core.model.SimulationConfig
import com.github.jozott00.wokwiintellij.core.ports.ProjectFiles
import com.github.jozott00.wokwiintellij.ide.services.IntelliJProjectFiles
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier.notifyBalloonAsync
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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

    private var licenseService: LicenseService = ApplicationManager.getApplication().service<WokwiLicensingService>()
    private val settingsState by lazy { project.service<WokwiSettingsState>() }
    private val projectFiles: ProjectFiles = IntelliJProjectFiles
    private val configResolver = WokwiProjectConfigResolver(project, projectFiles)
    private val json = Json {
        ignoreUnknownKeys = false
    }

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
        val diagram = withContext(Dispatchers.IO) { projectFiles.readString(config.diagramPath) }
        val firmware = loadFirmware(config.firmwarePath) ?: return null
        val customChips = loadCustomChips(config.customChips) ?: return null

        return LoadedSimulationConfig(
            simulationConfig = SimulationConfig(
                license = license,
                diagram = diagram,
                firmware = firmware,
                waitForDebugger = waitForDebugger,
                customChips = customChips,
            ),
            gdbServerPort = config.gdbServerPort,
        )
    }

    /**
     * Loads and classifies the firmware image at [firmwarePath].
     *
     * For regular firmware files this reads the file bytes directly. For ESP-IDF `flasher_args.json` files it delegates
     * to [EspIdfFirmwarePackager] to pack the referenced images and returns all watched paths needed for automatic reloads.
     *
     * Returns `null` after reporting a user-facing error when the firmware cannot be read or packed.
     */
    suspend fun loadFirmware(firmwarePath: Path): FirmwareImage? = withContext(Dispatchers.IO) {
        val firmwareFile = firmwarePath.normalize()
        if (!projectFiles.exists(firmwareFile)) {
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
                when (val result = EspIdfFirmwarePackager.pack(firmwareFile, projectFiles)) {
                    is FirmwarePackResult.Failure -> {
                        notifyBalloonAsync(
                            title = result.error.title,
                            message = result.error.message,
                            type = NotificationType.ERROR
                        )
                        return@withContext null
                    }

                    is FirmwarePackResult.Success -> result
                }

            watchPaths.addAll(packedResult.watchPaths)
            packedResult.image
        } else {
            projectFiles.readBytes(firmwareFile)
        }

        val format = FirmwareFormatDetector.detect(firmwareFile, buffer)

        FirmwareImage(
            buffer = buffer,
            format = format,
            rootPath = firmwareFile,
            isFlasherFile = isFlasherArgsFile,
            size = buffer.size.toUInt(),
            watchPaths = watchPaths
        )
    }

    private suspend fun loadCustomChips(chips: List<WokwiResolvedCustomChip>): List<CustomChip>? =
        withContext(Dispatchers.IO) {
            chips.map { chip ->
                val binary = readCustomChipBinary(chip) ?: return@withContext null
                val jsonConfig = readCustomChipJson(chip) ?: return@withContext null

                CustomChip(
                    name = chip.name,
                    binaryBase64 = Base64.getEncoder().encodeToString(binary),
                    json = jsonConfig,
                )
            }
        }

    private suspend fun readCustomChipBinary(chip: WokwiResolvedCustomChip): ByteArray? {
        if (!projectFiles.exists(chip.binaryPath)) {
            notifyBalloonAsync(
                title = "Failed to load custom chip",
                message = "Custom chip WASM `${chip.binaryPath}` does not exist and therefore cannot be loaded for simulation.",
                type = NotificationType.ERROR
            )
            return null
        }

        val buffer = projectFiles.readBytes(chip.binaryPath)
        if (buffer.isEmpty()) {
            notifyBalloonAsync(
                title = "Failed to load custom chip",
                message = "Custom chip WASM `${chip.binaryPath}` is empty and therefore cannot be loaded for simulation.",
                type = NotificationType.ERROR
            )
            return null
        }

        return buffer
    }

    private suspend fun readCustomChipJson(chip: WokwiResolvedCustomChip) =
        try {
            json.parseToJsonElement(projectFiles.readString(chip.jsonPath))
        } catch (e: SerializationException) {
            notifyBalloonAsync(
                title = "Failed to load custom chip",
                message = "Custom chip JSON `${chip.jsonPath}` is invalid. Full error message: ${e.message}",
                type = NotificationType.ERROR
            )
            null
        }

    private suspend fun loadLicense() = licenseService.loadAndCheckLicense()
        .onLeft {
            notifyBalloonAsync(
                title = it.title,
                message = it.message,
                type = NotificationType.ERROR
            )
        }.getOrNull()

}
