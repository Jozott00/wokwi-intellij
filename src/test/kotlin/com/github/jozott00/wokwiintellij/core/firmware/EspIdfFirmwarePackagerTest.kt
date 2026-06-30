package com.github.jozott00.wokwiintellij.core.firmware

import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EspIdfFirmwarePackagerTest {

    @Test
    fun `packs flash files into image by offset`() {
        val tempDir = Files.createTempDirectory("esp-idf-packager-test")
        val flasherArgs = tempDir.resolve("flasher_args.json")
        val bootloader = tempDir.resolve("bootloader.bin")
        val app = tempDir.resolve("app.bin")

        bootloader.writeBytes(byteArrayOf(1, 2))
        app.writeBytes(byteArrayOf(3, 4, 5))
        flasherArgs.writeText(
            """
            {
              "flash_files": {
                "0x0": "bootloader.bin",
                "0x4": "app.bin"
              }
            }
            """.trimIndent()
        )

        val result = assertIs<FirmwarePackResult.Success>(EspIdfFirmwarePackager.pack(flasherArgs))

        assertContentEquals(byteArrayOf(1, 2, 0, 0, 3, 4, 5), result.image)
        assertEquals(listOf(bootloader, app), result.watchPaths)
    }

    @Test
    fun `reports invalid flasher args json`() {
        val tempDir = Files.createTempDirectory("esp-idf-packager-test")
        val flasherArgs = tempDir.resolve("flasher_args.json")
        flasherArgs.writeText("{")

        val result = assertIs<FirmwarePackResult.Failure>(EspIdfFirmwarePackager.pack(flasherArgs))

        assertEquals("Failed to build image from flasher_args.json", result.error.title)
        assertEquals("Unable to parse content of flasher_args.json", result.error.message)
    }

    @Test
    fun `reports invalid flash offset`() {
        val tempDir = Files.createTempDirectory("esp-idf-packager-test")
        val flasherArgs = tempDir.resolve("flasher_args.json")
        flasherArgs.writeText(
            """
            {
              "flash_files": {
                "wat": "app.bin"
              }
            }
            """.trimIndent()
        )

        val result = assertIs<FirmwarePackResult.Failure>(EspIdfFirmwarePackager.pack(flasherArgs))

        assertEquals("Offset 'wat' is invalid", result.error.message)
    }

    @Test
    fun `reports missing firmware part`() {
        val tempDir = Files.createTempDirectory("esp-idf-packager-test")
        val flasherArgs = tempDir.resolve("flasher_args.json")
        flasherArgs.writeText(
            """
            {
              "flash_files": {
                "0x0": "missing.bin"
              }
            }
            """.trimIndent()
        )

        val result = assertIs<FirmwarePackResult.Failure>(EspIdfFirmwarePackager.pack(flasherArgs))

        assertEquals("Firmware part 'missing.bin' could not be found.", result.error.message)
    }
}
