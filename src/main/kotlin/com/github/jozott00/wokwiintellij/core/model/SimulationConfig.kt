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
)

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
