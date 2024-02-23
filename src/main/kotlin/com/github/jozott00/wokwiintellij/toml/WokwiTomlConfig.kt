package com.github.jozott00.wokwiintellij.toml

import kotlinx.serialization.Serializable


@Serializable
data class WokwiTomlConfig(
    val wokwi: WokwiTomlTable
)

@Serializable
data class WokwiTomlTable(
    val version: Int,
    val elf: String,
    val firmware: String,
    val gdbServerPort: Int? = null
)