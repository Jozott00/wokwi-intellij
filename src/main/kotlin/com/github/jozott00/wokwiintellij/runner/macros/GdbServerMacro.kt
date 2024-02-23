package com.github.jozott00.wokwiintellij.runner.macros

import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.intellij.ide.macro.Macro
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class GdbServerMacro : Macro() {
    override fun getName() = "WokwiGdbServer"

    override fun getDescription() = "Resolves to the Wokwi's GDB Server address"

    override fun expand(dataContext: DataContext): String? {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null
        val config = runBlocking(Dispatchers.IO) { WokwiConfigProcessor.readConfig(project) } ?: return null

        val port = config.gdbServerPort ?: return null
        return "localhost:$port"
    }
}