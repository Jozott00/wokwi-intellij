package com.github.jozott00.wokwiintellij.runner.macros

import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.intellij.ide.macro.Macro
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ElfPathMacro : Macro() {
    override fun getName() = "WokwiElfPath"

    override fun getDescription() = "Resolves to the ELF file path specified in the Wokwi configuration."

    override fun expand(dataContext: DataContext): String? {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null
        val config = runBlocking(Dispatchers.IO) { WokwiConfigProcessor.findElfFile(project) } ?: return null
        return config.path

        return "test"
    }
}