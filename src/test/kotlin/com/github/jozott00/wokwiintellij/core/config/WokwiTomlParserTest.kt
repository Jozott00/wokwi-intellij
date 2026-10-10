package com.github.jozott00.wokwiintellij.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WokwiTomlParserTest {

    @Test
    fun `parses wokwi table and ignores unknown names`() {
        val result = assertIs<WokwiTomlParseResult.Success>(
            WokwiTomlParser.parse(
                """
                [wokwi]
                version = 1
                elf = "build/firmware.elf"
                firmware = "build/firmware.bin"
                gdbServerPort = 3333
                unexpected = "ignored"
                """.trimIndent()
            )
        )

        assertEquals(1, result.config.wokwi.version)
        assertEquals("build/firmware.elf", result.config.wokwi.elf)
        assertEquals("build/firmware.bin", result.config.wokwi.firmware)
        assertEquals(3333, result.config.wokwi.gdbServerPort)
    }

    @Test
    fun `parses custom chip tables`() {
        val result = assertIs<WokwiTomlParseResult.Success>(
            WokwiTomlParser.parse(
                """
                [wokwi]
                version = 1
                elf = "build/firmware.elf"
                firmware = "build/firmware.bin"

                [[chip]]
                name = "my-chip"
                binary = "chips/my-chip.wasm"
                """.trimIndent()
            )
        )

        assertEquals(1, result.config.chip.size)
        assertEquals("my-chip", result.config.chip.single().name)
        assertEquals("chips/my-chip.wasm", result.config.chip.single().binary)
    }

    @Test
    fun `returns failure for malformed toml`() {
        assertIs<WokwiTomlParseResult.Failure>(
            WokwiTomlParser.parse(
                """
                [wokwi]
                version =
                """.trimIndent()
            )
        )
    }

    @Test
    fun `returns failure when wokwi table is missing`() {
        assertIs<WokwiTomlParseResult.Failure>(
            WokwiTomlParser.parse(
                """
                [other]
                version = 1
                """.trimIndent()
            )
        )
    }

    @Test
    fun `returns failure when required wokwi field is missing`() {
        assertIs<WokwiTomlParseResult.Failure>(
            WokwiTomlParser.parse(
                """
                [wokwi]
                version = 1
                elf = "build/firmware.elf"
                """.trimIndent()
            )
        )
    }
}
