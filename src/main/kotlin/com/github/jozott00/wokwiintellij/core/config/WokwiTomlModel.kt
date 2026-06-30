package com.github.jozott00.wokwiintellij.core.config

import kotlinx.serialization.Serializable

/**
 * Top-level `wokwi.toml` model.
 */
@Serializable
data class WokwiTomlConfig(
    val wokwi: WokwiTomlTable,
    val chip: List<CustomChipTomlTable> = emptyList(),
)

/**
 * Required simulator configuration from the `[wokwi]` TOML table.
 */
@Serializable
data class WokwiTomlTable(
    val version: Int,
    val elf: String,
    val firmware: String,
    val gdbServerPort: Int? = null,
)

/**
 * Custom chip entry from a `[[chip]]` TOML table.
 */
@Serializable
data class CustomChipTomlTable(
    val name: String,
    val binary: String,
)
