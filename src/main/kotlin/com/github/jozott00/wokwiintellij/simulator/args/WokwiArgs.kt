package com.github.jozott00.wokwiintellij.simulator.args

import com.intellij.openapi.vfs.VirtualFile


class WokwiArgs(
    val license: String,
    val diagram: String,
    var firmware: WokwiArgsFirmware,
    val projectType: WokwiProjectType,
)

class WokwiArgsFirmware(
    val buffer: ByteArray,
    val format: FirmwareFormat,
    val rootFile: VirtualFile,
    val isFlasherFile: Boolean,
    val size: UInt,
    val binaryPaths: List<String>
)

enum class FirmwareFormat {
    HEX,
    UF2,
    BIN
}

enum class WokwiProjectType {
    RUST,
    ZEPHYR,
    PLATFORMIO,
    ESP_IDF,
    PICO_SDK,
    ARDUINO,
    SMING,
    UNKNOWN
}