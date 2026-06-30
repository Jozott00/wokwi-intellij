package com.github.jozott00.wokwiintellij.core.config

import java.nio.file.Files
import java.nio.file.Path

object WokwiConfigResolver {

    fun resolve(
        configFile: Path,
        diagramFile: Path,
        config: WokwiTomlConfig,
    ): WokwiConfigResolveResult {
        val configDir = configFile.parent ?: Path.of(".")
        val elfPath = configDir.resolve(config.wokwi.elf).normalize()
        val firmwarePath = configDir.resolve(config.wokwi.firmware).normalize()
        val resolvedDiagramPath = diagramFile.normalize()
        val customChips = config.chip.map { chip ->
            val binaryPath = configDir.resolve(chip.binary).normalize()
            val jsonPath = configDir.resolve(chip.binary.removeSuffix(".wasm") + ".json").normalize()
            WokwiResolvedCustomChip(
                name = chip.name,
                binaryPath = binaryPath,
                jsonPath = jsonPath,
            )
        }

        if (!Files.exists(elfPath)) {
            return WokwiConfigResolveResult.Failure(WokwiConfigResolveError.InvalidElfPath)
        }

        if (!Files.exists(firmwarePath)) {
            return WokwiConfigResolveResult.Failure(WokwiConfigResolveError.InvalidFirmwarePath)
        }

        if (!Files.exists(resolvedDiagramPath)) {
            return WokwiConfigResolveResult.Failure(WokwiConfigResolveError.InvalidDiagramPath)
        }

        customChips.forEach { chip ->
            if (!Files.exists(chip.binaryPath)) {
                return WokwiConfigResolveResult.Failure(WokwiConfigResolveError.InvalidCustomChipBinaryPath)
            }

            if (!Files.exists(chip.jsonPath)) {
                return WokwiConfigResolveResult.Failure(WokwiConfigResolveError.InvalidCustomChipJsonPath)
            }
        }

        return WokwiConfigResolveResult.Success(
            WokwiResolvedConfig(
                firmwarePath = firmwarePath,
                elfPath = elfPath,
                diagramPath = resolvedDiagramPath,
                gdbServerPort = config.wokwi.gdbServerPort,
                customChips = customChips,
            )
        )
    }
}

sealed interface WokwiConfigResolveResult {
    data class Success(val config: WokwiResolvedConfig) : WokwiConfigResolveResult
    data class Failure(val error: WokwiConfigResolveError) : WokwiConfigResolveResult
}

data class WokwiResolvedConfig(
    val firmwarePath: Path,
    val elfPath: Path,
    val diagramPath: Path,
    val gdbServerPort: Int?,
    val customChips: List<WokwiResolvedCustomChip> = emptyList(),
)

data class WokwiResolvedCustomChip(
    val name: String,
    val binaryPath: Path,
    val jsonPath: Path,
)

enum class WokwiConfigResolveError {
    InvalidElfPath,
    InvalidFirmwarePath,
    InvalidDiagramPath,
    InvalidCustomChipBinaryPath,
    InvalidCustomChipJsonPath,
}
