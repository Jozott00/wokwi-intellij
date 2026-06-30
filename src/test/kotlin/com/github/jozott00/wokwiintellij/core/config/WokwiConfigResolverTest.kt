package com.github.jozott00.wokwiintellij.core.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WokwiConfigResolverTest {

    @Test
    fun `resolves project-relative firmware elf and diagram paths`() {
        val projectDir = Files.createTempDirectory("wokwi-config-resolver-test")
        val buildDir = Files.createDirectories(projectDir.resolve("build"))
        val configFile = projectDir.resolve("wokwi.toml")
        val diagramFile = projectDir.resolve("diagram.json")
        val elfFile = buildDir.resolve("firmware.elf")
        val firmwareFile = buildDir.resolve("firmware.bin")
        Files.createFile(configFile)
        Files.createFile(diagramFile)
        Files.createFile(elfFile)
        Files.createFile(firmwareFile)

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
            )
        )

        assertEquals(elfFile, result.config.elfPath)
        assertEquals(firmwareFile, result.config.firmwarePath)
        assertEquals(diagramFile, result.config.diagramPath)
        assertEquals(3333, result.config.gdbServerPort)
    }

    @Test
    fun `reports invalid elf path before firmware path`() {
        val projectDir = Files.createTempDirectory("wokwi-config-resolver-test")
        val configFile = projectDir.resolve("wokwi.toml")
        val diagramFile = projectDir.resolve("diagram.json")
        Files.createFile(configFile)
        Files.createFile(diagramFile)

        val result = assertIs<WokwiConfigResolveResult.Failure>(
            WokwiConfigResolver.resolve(
                configFile = configFile,
                diagramFile = diagramFile,
                config = WokwiTomlTable(
                    version = 1,
                    elf = "missing.elf",
                    firmware = "missing.bin",
                ).toConfig(),
            )
        )

        assertEquals(WokwiConfigResolveError.InvalidElfPath, result.error)
    }

    @Test
    fun `reports invalid firmware path after elf is valid`() {
        val projectDir = Files.createTempDirectory("wokwi-config-resolver-test")
        val configFile = projectDir.resolve("wokwi.toml")
        val diagramFile = projectDir.resolve("diagram.json")
        val elfFile = projectDir.resolve("firmware.elf")
        Files.createFile(configFile)
        Files.createFile(diagramFile)
        Files.createFile(elfFile)

        val result = assertIs<WokwiConfigResolveResult.Failure>(
            WokwiConfigResolver.resolve(
                configFile = configFile,
                diagramFile = diagramFile,
                config = WokwiTomlTable(
                    version = 1,
                    elf = "firmware.elf",
                    firmware = "missing.bin",
                ).toConfig(),
            )
        )

        assertEquals(WokwiConfigResolveError.InvalidFirmwarePath, result.error)
    }

    @Test
    fun `reports invalid diagram path after elf and firmware are valid`() {
        val projectDir = Files.createTempDirectory("wokwi-config-resolver-test")
        val configFile = projectDir.resolve("wokwi.toml")
        val elfFile = projectDir.resolve("firmware.elf")
        val firmwareFile = projectDir.resolve("firmware.bin")
        Files.createFile(configFile)
        Files.createFile(elfFile)
        Files.createFile(firmwareFile)

        val result = assertIs<WokwiConfigResolveResult.Failure>(
            WokwiConfigResolver.resolve(
                configFile = configFile,
                diagramFile = projectDir.resolve("missing-diagram.json"),
                config = WokwiTomlTable(
                    version = 1,
                    elf = "firmware.elf",
                    firmware = "firmware.bin",
                ).toConfig(),
            )
        )

        assertEquals(WokwiConfigResolveError.InvalidDiagramPath, result.error)
    }

    @Test
    fun `resolves custom chip binary and json paths`() {
        val projectDir = Files.createTempDirectory("wokwi-config-resolver-test")
        val chipsDir = Files.createDirectories(projectDir.resolve("chips"))
        val configFile = projectDir.resolve("wokwi.toml")
        val diagramFile = projectDir.resolve("diagram.json")
        val elfFile = projectDir.resolve("firmware.elf")
        val firmwareFile = projectDir.resolve("firmware.bin")
        val chipBinary = chipsDir.resolve("my-chip.wasm")
        val chipJson = chipsDir.resolve("my-chip.json")
        Files.createFile(configFile)
        Files.createFile(diagramFile)
        Files.createFile(elfFile)
        Files.createFile(firmwareFile)
        Files.createFile(chipBinary)
        Files.createFile(chipJson)

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
            )
        )

        assertEquals("my-chip", result.config.customChips.single().name)
        assertEquals(chipBinary, result.config.customChips.single().binaryPath)
        assertEquals(chipJson, result.config.customChips.single().jsonPath)
    }

    private fun WokwiTomlTable.toConfig() = WokwiTomlConfig(wokwi = this)
}
