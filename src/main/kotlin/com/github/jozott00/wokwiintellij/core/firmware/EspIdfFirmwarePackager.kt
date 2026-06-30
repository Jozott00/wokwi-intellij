package com.github.jozott00.wokwiintellij.core.firmware

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

object EspIdfFirmwarePackager {
    private const val MAX_FIRMWARE_SIZE = 16 * 1024 * 1024
    private const val ERROR_TITLE = "Failed to build image from flasher_args.json"

    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun pack(flasherArgs: Path): FirmwarePackResult {
        fun error(message: String) = FirmwarePackResult.Failure(FirmwarePackError(ERROR_TITLE, message))

        val flasherJson = try {
            jsonParser.decodeFromString<FlasherJson>(Files.readString(flasherArgs))
        } catch (_: IllegalArgumentException) {
            return error("Unable to parse content of flasher_args.json")
        }

        if (flasherJson.flashFiles.isEmpty()) {
            return error("No firmware parts were listed in flasher_args.json")
        }

        val partPaths = mutableListOf<Path>()
        val firmwareParts = flasherJson.flashFiles.entries.map { entry ->
            val offset = entry.key.removePrefix("0x").toIntOrNull(16)
                ?: return error("Offset '${entry.key}' is invalid")

            val partFile = flasherArgs.parent.resolve(entry.value).normalize()
            if (!Files.exists(partFile)) {
                return error("Firmware part '${entry.value}' could not be found.")
            }

            partPaths.add(partFile)
            FirmwarePart(offset, Files.readAllBytes(partFile))
        }

        val firmwareSize = firmwareParts.maxOf { it.offset + it.data.size }
        if (firmwareSize > MAX_FIRMWARE_SIZE) {
            return error(
                "Firmware size ($firmwareSize bytes) exceeds the maximum supported size ($MAX_FIRMWARE_SIZE bytes)"
            )
        }

        val firmwareData = ByteArray(firmwareSize)
        firmwareParts.forEach { part ->
            part.data.copyInto(firmwareData, part.offset)
        }

        return FirmwarePackResult.Success(firmwareData, partPaths)
    }

    private data class FirmwarePart(
        val offset: Int,
        val data: ByteArray,
    )
}

sealed interface FirmwarePackResult {
    data class Success(
        val image: ByteArray,
        val watchPaths: List<Path>,
    ) : FirmwarePackResult

    data class Failure(
        val error: FirmwarePackError,
    ) : FirmwarePackResult
}

data class FirmwarePackError(
    val title: String,
    val message: String,
)

@Serializable
private data class FlasherJson(
    @SerialName("flash_files")
    val flashFiles: Map<String, String> = emptyMap(),
)
