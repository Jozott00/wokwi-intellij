package com.github.jozott00.wokwiintellij.utils.simulation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.jozott00.wokwiintellij.exceptions.GenericError
import com.github.jozott00.wokwiintellij.exceptions.catchIllArg
import com.github.jozott00.wokwiintellij.extensions.hexStringToByteArray
import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readBytes
import com.intellij.openapi.vfs.readText
import io.ktor.util.collections.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object FirmwareUtils {

    private const val MAX_FIRMWARE_SIZE = 16 * 1024 * 1024
    private val LOG = logger<FirmwareUtils>()

    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun packEspIdfFirmware(flasherArgs: VirtualFile): Either<GenericError, EspIdfPackResult> = withContext(Dispatchers.IO) {
        LOG.info("Packing ESP-IDF image from flasher-args '${flasherArgs.path}'")
        fun buildErrorResult(message: String) = GenericError("Failed to build image from flasher_args.json", message).left()

        val flasherArgsString = readAction { flasherArgs.readText() }
        val flasherJson =  catchIllArg { jsonParser.decodeFromString<FlasherJson>(flasherArgsString) }
            .getOrElse {
                thisLogger().warn(it)
                return@withContext buildErrorResult("Unable to parse content of flasher_args.json") }


        val partPaths = ConcurrentSet<String>()

        // list of (offset, data)
        val firmwareParts = flasherJson.flashFiles.entries.map {e ->
            val offset = e.key.removePrefix("0x").toIntOrNull(16)
                ?: return@withContext buildErrorResult("Offset '${e.key}' is invalid")

            val partFile = flasherArgs.parent.findFileByRelativePath(e.value)
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

    fun determineFirmwareFormat(file: VirtualFile, content: ByteArray): FirmwareFormat {
        val isUf2 = content.size >= 512 && isUf2Block(content.sliceArray(0 until 512))
        if (isUf2)
            return FirmwareFormat.UF2

        val fileExtension = file.extension?.lowercase()
        val format = when (fileExtension) {
            "hex" -> FirmwareFormat.HEX
            else -> FirmwareFormat.BIN
        }

        return format
    }

    private fun isUf2Block(block: ByteArray): Boolean {
        if (block.size != 512)
            return false

        val firstMagicNumber = block.sliceArray(0 until 4)
        val expectedFirstMagicNumber = "0x0A324655".hexStringToByteArray()!!
        if (!firstMagicNumber.contentEquals(expectedFirstMagicNumber))
            return false

        val secondMagicNumber = block.sliceArray(4 until 8)
        val expectedSecondMagicNumber = "0x9E5D5157".hexStringToByteArray()!!
        if (!secondMagicNumber.contentEquals(expectedSecondMagicNumber))
            return false

        val finalMagicNumber = block.sliceArray(block.size - 4 until block.size)
        val expectedFinalMagicNumber = "0x0AB16F30".hexStringToByteArray()!!
        return finalMagicNumber.contentEquals(expectedFinalMagicNumber)
    }


    class EspIdfPackResult(
        val img: ByteArray,
        val binaryPaths: List<String>
    )
}

@Serializable
private data class FlasherJson(
    @SerialName("flash_files")
    val flashFiles: Map<String, String>
)