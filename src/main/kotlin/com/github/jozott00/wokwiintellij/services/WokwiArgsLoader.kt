package com.github.jozott00.wokwiintellij.services

import arrow.core.Either
import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgsFirmware
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier.notifyBalloonAsync
import com.github.jozott00.wokwiintellij.utils.simulation.FirmwareUtils
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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

        val args = WokwiArgs(license, diagram, firmware)
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

        val isFlasherArgsFile = firmwareFile.name == "flasher_args.json"
        val binaryPaths = mutableListOf(firmwareFile.path)

        val buffer = if (isFlasherArgsFile) {
            val packedResult =
                when (val result = FirmwareUtils.packEspIdfFirmware(firmwareFile)) {
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

        val format = FirmwareUtils.determineFirmwareFormat(firmwareFile, buffer)

        WokwiArgsFirmware(
            buffer = buffer,
            format = format,
            rootFile = firmwareFile,
            isFlasherFile = isFlasherArgsFile,
            size = buffer.size.toUInt(),
            binaryPaths = binaryPaths
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