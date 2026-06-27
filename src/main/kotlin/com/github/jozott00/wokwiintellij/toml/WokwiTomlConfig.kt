package com.github.jozott00.wokwiintellij.toml

import kotlinx.serialization.Serializable

/**
 * Wokwi.toml serialized configurations.
 */
@Serializable
data class WokwiTomlConfig(
    val wokwi: WokwiTomlTable,
    val chip: List<CustomChipTomlTable> = emptyList()
)

/**
 * General simulation wokwi.toml configs data class.
 */
@Serializable
data class WokwiTomlTable(
    val version: Int,
    val elf: String,
    val firmware: String,
    val gdbServerPort: Int? = null
)
/**
 * Wokwi.toml custom chip configs data class.
 */
@Serializable
data class CustomChipTomlTable(
    val name: String,
    val binary: String,
)