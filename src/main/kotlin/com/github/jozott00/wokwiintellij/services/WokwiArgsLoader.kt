package com.github.jozott00.wokwiintellij.services

import arrow.core.Either
import com.github.jozott00.wokwiintellij.exceptions.GenericError
import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgsFirmware
import com.github.jozott00.wokwiintellij.simulator.args.WokwiProjectType
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier.notifyBalloonAsync
import com.github.jozott00.wokwiintellij.utils.simulation.FirmwareUtils
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readBytes
import com.intellij.openapi.vfs.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class WokwiArgsLoader(val project: Project) {

    private var licensingService = ApplicationManager.getApplication().service<WokwiLicensingService>()

    suspend fun load(config: WokwiConfig): WokwiArgs? {
        val license = loadLicense() ?: return null
        val diagram = readAction { config.diagram.readText() }
        val firmware = loadFirmware(config.firmware) ?: return null

        val projectType = detectProject()
        // TODO: Check for esp image

        val args = WokwiArgs(license, diagram, firmware, projectType)
        return args

    }

    suspend fun loadFirmware(firmwareFile: VirtualFile): WokwiArgsFirmware? = withContext(Dispatchers.IO) {
        if (!readAction { firmwareFile.exists() }) {
            withContext(Dispatchers.EDT) {
                notifyBalloonAsync(
                    title = "Failed to load firmware",
                    message = "Firmware `${firmwareFile.path}` does not exist and therefore cannot be loaded for simulation.",
                    NotificationType.ERROR
                )
            }
            return@withContext null
        }

        val isFirmwareFile = firmwareFile.name == "flasher_args.json"
        val format: FirmwareFormat = when {
            firmwareFile.extension.equals("hex", ignoreCase = true) -> FirmwareFormat.HEX
            firmwareFile.extension.equals("uf2", ignoreCase = true) -> FirmwareFormat.UF2
            else -> FirmwareFormat.BIN
        }

        val binaryPaths = mutableListOf(firmwareFile.path)
        val buffer = if (isFirmwareFile) {
            val packedResult=
                when (val result = FirmwareUtils.packEspIdfFirmware(firmwareFile, project)) {
                is Either.Left -> {
                    notifyBalloonAsync(result.value)
                    return@withContext null
                }
                is Either.Right -> result.value
            }

            binaryPaths.addAll(packedResult.binaryPaths)
            packedResult.img
        } else {
            readAction { firmwareFile.readBytes() }
        }


        WokwiArgsFirmware(
            buffer = buffer,
            format = format,
            rootFile = firmwareFile,
            isFlasherFile = false,
            size = buffer.size.toUInt(),
            binaryPaths = binaryPaths
        )
    }

    private suspend fun detectProject(): WokwiProjectType {
        return WokwiProjectType.RUST
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