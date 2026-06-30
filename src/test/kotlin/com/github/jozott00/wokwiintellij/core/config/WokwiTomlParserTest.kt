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

        assertEquals(1, result.config.version)
        assertEquals("build/firmware.elf", result.config.elf)
        assertEquals("build/firmware.bin", result.config.firmware)
        assertEquals(3333, result.config.gdbServerPort)
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
