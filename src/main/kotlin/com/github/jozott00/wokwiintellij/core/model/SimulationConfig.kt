package com.github.jozott00.wokwiintellij.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

data class SimulationConfig(
    val license: String,
    val diagram: String,
    val firmware: FirmwareImage,
    val waitForDebugger: Boolean = false,
    val customChips: List<CustomChip> = emptyList(),
)

data class FirmwareImage(
    val buffer: ByteArray,
    val format: FirmwareFormat,
    val rootPath: Path,
    val isFlasherFile: Boolean,
    val size: UInt,
    val watchPaths: List<Path>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirmwareImage) return false
        return buffer.contentEquals(other.buffer)
            && format == other.format
            && rootPath == other.rootPath
            && isFlasherFile == other.isFlasherFile
            && size == other.size
            && watchPaths == other.watchPaths
    }

    override fun hashCode(): Int {
        var result = buffer.contentHashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + rootPath.hashCode()
        result = 31 * result + isFlasherFile.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + watchPaths.hashCode()
        return result
    }
}

enum class FirmwareFormat {
    HEX,
    UF2,
    BIN;

    override fun toString() = name.lowercase()
}

@Serializable
data class CustomChip(
    val name: String,
    val binaryBase64: String,
    val json: JsonElement,
)
