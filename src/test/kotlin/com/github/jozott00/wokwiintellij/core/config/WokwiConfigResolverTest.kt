package com.github.jozott00.wokwiintellij.core.config

import com.github.jozott00.wokwiintellij.TestProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WokwiConfigResolverTest {

    @Test
    fun `resolves project-relative firmware elf and diagram paths`() {
        val projectFiles = TestProjectFiles()
        val configFile = projectFiles.addFile("wokwi.toml")
        val diagramFile = projectFiles.addFile("diagram.json")
        val elfFile = projectFiles.addFile("build/firmware.elf")
        val firmwareFile = projectFiles.addFile("build/firmware.bin")

        val result = assertIs<WokwiConfigResolveResult.Success>(
            WokwiConfigResolver.resolve(
                configFile = configFile,
                diagramFile = diagramFile,
                config = WokwiTomlTable(
                    version = 1,
                    elf = "build/firmware.elf",
                    firmware = "build/firmware.bin",
                    gdbServerPort = 3333,
                ).toConfig(),
                projectFiles = projectFiles,
            )
        )

        assertEquals(elfFile, result.config.elfPath)
        assertEquals(firmwareFile, result.config.firmwarePath)
        assertEquals(diagramFile, result.config.diagramPath)
        assertEquals(3333, result.config.gdbServerPort)
    }

    @Test
    fun `reports invalid elf path before firmware path`() {
        val projectFiles = TestProjectFiles()
        val configFile = projectFiles.addFile("wokwi.toml")
        val diagramFile = projectFiles.addFile("diagram.json")

        val result = assertIs<WokwiConfigResolveResult.Failure>(
            WokwiConfigResolver.resolve(
                configFile = configFile,
                diagramFile = diagramFile,
                config = WokwiTomlTable(
                    version = 1,
                    elf = "missing.elf",
                    firmware = "missing.bin",
                ).toConfig(),
                projectFiles = projectFiles,
            )
        )

        assertEquals(WokwiConfigResolveError.InvalidElfPath, result.error)
    }

    @Test
    fun `reports invalid firmware path after elf is valid`() {
        val projectFiles = TestProjectFiles()
        val configFile = projectFiles.addFile("wokwi.toml")
        val diagramFile = projectFiles.addFile("diagram.json")
        projectFiles.addFile("firmware.elf")

        val result = assertIs<WokwiConfigResolveResult.Failure>(
            WokwiConfigResolver.resolve(
                configFile = configFile,
                diagramFile = diagramFile,
                config = WokwiTomlTable(
                    version = 1,
                    elf = "firmware.elf",
                    firmware = "missing.bin",
                ).toConfig(),
                projectFiles = projectFiles,
            )
        )

        assertEquals(WokwiConfigResolveError.InvalidFirmwarePath, result.error)
    }

    @Test
    fun `reports invalid diagram path after elf and firmware are valid`() {
        val projectFiles = TestProjectFiles()
        val configFile = projectFiles.addFile("wokwi.toml")
        projectFiles.addFile("firmware.elf")
        projectFiles.addFile("firmware.bin")

        val result = assertIs<WokwiConfigResolveResult.Failure>(
            WokwiConfigResolver.resolve(
                configFile = configFile,
                diagramFile = projectFiles.path("missing-diagram.json"),
                config = WokwiTomlTable(
                    version = 1,
                    elf = "firmware.elf",
                    firmware = "firmware.bin",
                ).toConfig(),
                projectFiles = projectFiles,
            )
        )

        assertEquals(WokwiConfigResolveError.InvalidDiagramPath, result.error)
    }

    @Test
    fun `resolves custom chip binary and json paths`() {
        val projectFiles = TestProjectFiles()
        val configFile = projectFiles.addFile("wokwi.toml")
        val diagramFile = projectFiles.addFile("diagram.json")
        projectFiles.addFile("firmware.elf")
        projectFiles.addFile("firmware.bin")
        val chipBinary = projectFiles.addFile("chips/my-chip.wasm")
        val chipJson = projectFiles.addFile("chips/my-chip.json")

        val result = assertIs<WokwiConfigResolveResult.Success>(
            WokwiConfigResolver.resolve(
                configFile = configFile,
                diagramFile = diagramFile,
                config = WokwiTomlConfig(
                    wokwi = WokwiTomlTable(
                        version = 1,
                        elf = "firmware.elf",
                        firmware = "firmware.bin",
                    ),
                    chip = listOf(
                        CustomChipTomlTable(
                            name = "my-chip",
                            binary = "chips/my-chip.wasm",
                        )
                    ),
                ),
                projectFiles = projectFiles,
            )
        )

        assertEquals("my-chip", result.config.customChips.single().name)
        assertEquals(chipBinary, result.config.customChips.single().binaryPath)
        assertEquals(chipJson, result.config.customChips.single().jsonPath)
    }

    private fun WokwiTomlTable.toConfig() = WokwiTomlConfig(wokwi = this)
}
