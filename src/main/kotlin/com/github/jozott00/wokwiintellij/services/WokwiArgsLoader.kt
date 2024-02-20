package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgsFirmware
import com.github.jozott00.wokwiintellij.simulator.args.WokwiProjectType
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier.notifyBalloonAsync
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
import java.util.*

@Service(Service.Level.PROJECT)
class WokwiArgsLoader(project: Project) {

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

    suspend fun loadFirmware(firmwareFile: VirtualFile): WokwiArgsFirmware? {
        if (!readAction { firmwareFile.exists() }) {
            withContext(Dispatchers.EDT) {
                notifyBalloonAsync(
                    title = "Failed to load firmware",
                    message = "Firmware `${firmwareFile.path}` does not exist and therefore cannot be loaded for simulation.",
                    NotificationType.ERROR
                )
            }
            return null
        }

        val buffer = readAction { firmwareFile.readBytes() }
        val binaryPaths = listOf(firmwareFile.path)

        val firmware = WokwiArgsFirmware(
            buffer = buffer,
            format = FirmwareFormat.BIN,
            rootFile = firmwareFile,
            isFlasherFile = false,
            size = buffer.size.toUInt(),
            binaryPaths = binaryPaths
        )

        return firmware

    }

    private suspend fun detectProject(): WokwiProjectType {
        return WokwiProjectType.RUST
    }

    private suspend fun loadLicense(): String? {
        val license = licensingService.getLicense() ?: run {
            notifyBalloonAsync(
                "No Wokwi license found",
                "Set your Wokwi license in the Wokwi window.",
                NotificationType.ERROR
            )
            return@loadLicense null
        }

        val licenseObj = licensingService.parseLicense(license) ?: run {
            notifyBalloonAsync(
                "Invalid Wokwi license",
                "The Wokwi license could not be parsed.",
                NotificationType.ERROR
            )
            return@loadLicense null
        }

        if (licenseObj.expiration < Date()) {
            notifyBalloonAsync(
                "Expired Wokwi license",
                "The Wokwi license is expired, please refresh it.",
                NotificationType.ERROR
            )
            return null
        }

        return license
    }


}