package com.github.jozott00.wokwiintellij.toml

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class WokwiTomlConfig(
    val wokwi: WokwiTomlTable,
    val chip: List<CustomChipTomlTable> = emptyList()
)

@Serializable
data class WokwiTomlTable(
    val version: Int,
    val elf: String,
    val firmware: String,
    val gdbServerPort: Int? = null
)

@Serializable
data class CustomChipTomlTable(
    val name: String,
    val binary: String,
)
