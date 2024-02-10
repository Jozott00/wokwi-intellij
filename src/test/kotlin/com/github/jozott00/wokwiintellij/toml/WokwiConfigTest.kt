package com.github.jozott00.wokwiintellij.toml

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlFileType

class WokwiConfigTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(TomlFileType, exampleConfig)
        val tomlFile = assertInstanceOf(psiFile, TomlFile::class.java)

        val list = tomlFile.tableList

        println("toml file: $tomlFile")

    }


    private val exampleConfig = """
        [wokwi]
        version = 1
        elf = ".pio/build/esp32/firmware.elf"
        firmware = ".pio/build/esp32/firmware.bin"
    """.trimIndent()

}