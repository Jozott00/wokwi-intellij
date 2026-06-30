@file:Suppress("unused")

package com.github.jozott00.wokwiintellij.simulator

import kotlinx.serialization.Serializable
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.JsonElement

class WokwiConfig(
    val version: String,
    val elf: VirtualFile,
    val firmware: VirtualFile,
    val diagram: VirtualFile,
    val gdbServerPort: Int?,
    val chips : List<WokwiCustomChip>
)

@Serializable
data class WokwiCustomChip (
    val name : String,
    val binaryBase64 : String,
    val json : JsonElement
)
