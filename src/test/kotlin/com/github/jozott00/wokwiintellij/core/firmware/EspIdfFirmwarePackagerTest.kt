package com.github.jozott00.wokwiintellij.core.firmware

import com.github.jozott00.wokwiintellij.TestProjectFiles
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EspIdfFirmwarePackagerTest {

    @Test
    fun `packs flash files into image by offset`() {
        val projectFiles = TestProjectFiles()
        val flasherArgs = projectFiles.addFile(
            "flasher_args.json",
            """
            {
              "flash_files": {
                "0x0": "bootloader.bin",
                "0x4": "app.bin"
              }
            }
            """.trimIndent()
        )
        val bootloader = projectFiles.addFile("bootloader.bin", byteArrayOf(1, 2))
        val app = projectFiles.addFile("app.bin", byteArrayOf(3, 4, 5))

        val result = assertIs<FirmwarePackResult.Success>(EspIdfFirmwarePackager.pack(flasherArgs, projectFiles))

        assertContentEquals(byteArrayOf(1, 2, 0, 0, 3, 4, 5), result.image)
        assertEquals(listOf(bootloader, app), result.watchPaths)
    }

    @Test
    fun `reports invalid flasher args json`() {
        val projectFiles = TestProjectFiles()
        val flasherArgs = projectFiles.addFile("flasher_args.json", "{")

        val result = assertIs<FirmwarePackResult.Failure>(EspIdfFirmwarePackager.pack(flasherArgs, projectFiles))

        assertEquals("Failed to build image from flasher_args.json", result.error.title)
        assertEquals("Unable to parse content of flasher_args.json", result.error.message)
    }

    @Test
    fun `reports invalid flash offset`() {
        val projectFiles = TestProjectFiles()
        val flasherArgs = projectFiles.addFile(
            "flasher_args.json",
            """
            {
              "flash_files": {
                "wat": "app.bin"
              }
            }
            """.trimIndent()
        )

        val result = assertIs<FirmwarePackResult.Failure>(EspIdfFirmwarePackager.pack(flasherArgs, projectFiles))

        assertEquals("Offset 'wat' is invalid", result.error.message)
    }

    @Test
    fun `reports missing firmware part`() {
        val projectFiles = TestProjectFiles()
        val flasherArgs = projectFiles.addFile(
            "flasher_args.json",
            """
            {
              "flash_files": {
                "0x0": "missing.bin"
              }
            }
            """.trimIndent()
        )

        val result = assertIs<FirmwarePackResult.Failure>(EspIdfFirmwarePackager.pack(flasherArgs, projectFiles))

        assertEquals("Firmware part 'missing.bin' could not be found.", result.error.message)
    }
}
