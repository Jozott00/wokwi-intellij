package com.github.jozott00.wokwiintellij.utils.simulation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.jozott00.wokwiintellij.exceptions.GenericError
import com.github.jozott00.wokwiintellij.exceptions.catchIllArg
import com.github.jozott00.wokwiintellij.extensions.findRelativeFiles
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readBytes
import com.intellij.openapi.vfs.readText
import io.ktor.util.collections.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

object FirmwareUtils {

    const val MAX_FIRMWARE_SIZE = 16 * 1024 * 1024;

    suspend fun packEspIdfFirmware(flasherArgs: VirtualFile, project: Project): Either<GenericError, EspIdfPackResult> = withContext(Dispatchers.IO) {
        fun buildErrorResult(message: String) = GenericError("Failed to build image from flasher_args.json", message).left()

        val flasherArgsString = readAction { flasherArgs.readText() }
        val flasherJson =  catchIllArg { Json.decodeFromString<FlasherJson>(flasherArgsString) }
            .getOrElse { return@withContext buildErrorResult("Unable to parse content of flasher_args.json") }


        val partPaths = ConcurrentSet<String>()

        // list of (offset, data)
        val firmwareParts = flasherJson.flashFiles.entries.map {e ->
            val offset = e.key.toIntOrNull()
                ?: return@withContext buildErrorResult("Offset '${e.key}' is invalid")
            val partFile = flasherArgs.findFileByRelativePath(e.value)
                ?: return@withContext buildErrorResult("Firmware part '${e.value}' could not be found.")

            val data = readAction { partFile.readBytes() }
            partPaths.add(partFile.path)
            Pair(offset, data)
        }

        val firmwareSize = firmwareParts.maxOf { it.first + it.second.size }
        if (firmwareSize > MAX_FIRMWARE_SIZE)
            return@withContext buildErrorResult("Firmware size ($firmwareSize bytes) exceeds the maximum supported size ($MAX_FIRMWARE_SIZE bytes)")

        val firmwareData = ByteArray(firmwareSize)
        firmwareParts.forEach { (offset, data) ->
                data.copyInto(firmwareData, offset)
            }

        EspIdfPackResult(firmwareData, partPaths.toList()).right()
    }

    class EspIdfPackResult(
        val img: ByteArray,
        val binaryPaths: List<String>
    )
}

private data class FlasherJson(
    @SerialName("flash_files")
    val flashFiles: Map<String, String>
)