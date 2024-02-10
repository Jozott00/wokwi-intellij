package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.simulator.args.*
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier.notifyBalloon
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readBytes
import com.intellij.openapi.vfs.readText

@Service(Service.Level.PROJECT)
class WokwiArgsLoader(project: Project) {

    fun load(config: WokwiConfig): WokwiArgs? {
        val diagram = config.diagram.readText()
        val firmware = loadFirmware(config.firmware) ?: return null

        val projectType = detectProject()
        // TODO: Check for esp image


        val args = WokwiArgs(diagram, firmware, projectType)
        return args
    }

    fun loadFirmware(firmwareFile: VirtualFile): WokwiArgsFirmware? {
        if (!firmwareFile.exists()) {
            notifyBalloon(
                title = "Failed to load firmware",
                message = "Firmware `${firmwareFile.path}` does not exist and therefore cannot be loaded for simulation."
            )
            return null
        }

        val buffer = firmwareFile.readBytes()
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

    private fun detectProject(): WokwiProjectType {
        return WokwiProjectType.RUST
    }

}