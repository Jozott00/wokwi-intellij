package com.github.jozott00.wokwiintellij.core.config

import java.nio.file.Files
import java.nio.file.Path

object WokwiConfigResolver {

    fun resolve(
        configFile: Path,
        diagramFile: Path,
        config: WokwiTomlTable,
    ): WokwiConfigResolveResult {
        val configDir = configFile.parent ?: Path.of(".")
        val elfPath = configDir.resolve(config.elf).normalize()
        val firmwarePath = configDir.resolve(config.firmware).normalize()
        val resolvedDiagramPath = diagramFile.normalize()

        if (!Files.exists(elfPath)) {
            return WokwiConfigResolveResult.Failure(WokwiConfigResolveError.InvalidElfPath)
        }

        if (!Files.exists(firmwarePath)) {
            return WokwiConfigResolveResult.Failure(WokwiConfigResolveError.InvalidFirmwarePath)
        }

        if (!Files.exists(resolvedDiagramPath)) {
            return WokwiConfigResolveResult.Failure(WokwiConfigResolveError.InvalidDiagramPath)
        }

        return WokwiConfigResolveResult.Success(
            WokwiResolvedConfig(
                firmwarePath = firmwarePath,
                elfPath = elfPath,
                diagramPath = resolvedDiagramPath,
                gdbServerPort = config.gdbServerPort,
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
)

enum class WokwiConfigResolveError {
    InvalidElfPath,
    InvalidFirmwarePath,
    InvalidDiagramPath,
}
