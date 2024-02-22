package com.github.jozott00.wokwiintellij.toml.inspections

import com.github.jozott00.wokwiintellij.ide.WokwiFileType
import com.github.jozott00.wokwiintellij.toml.stringValue
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlVisitor

open class WokwiConfigVisitor : TomlVisitor() {

    open fun visitWokwiTable(value: TomlTable) {
        for (e in value.entries) {
            visitWokwiKeyValue(e)
        }
    }

    open fun visitVersionValue(value: TomlKeyValue) {

    }

    open fun visitElfValue(value: TomlKeyValue) {

    }

    open fun visitFirmwareValue(value: TomlKeyValue) {

    }

    open fun visitUnknownTable(value: TomlTable) {

    }

    open fun visitUnknownWokwiValue(value: TomlKeyValue) {

    }

    private fun visitWokwiKeyValue(element: TomlKeyValue) {
        when (element.key.stringValue) {
            "version" -> visitVersionValue(element)
            "elf" -> visitElfValue(element)
            "firmware" -> visitFirmwareValue(element)
            else -> visitUnknownWokwiValue(element)
        }
    }

    override fun visitTable(element: TomlTable) {
        super.visitTable(element)

        if (element.header.key?.stringValue == "wokwi") {
            return visitWokwiTable(element)
        }
        visitUnknownTable(element)
    }


}