package com.github.jozott00.wokwiintellij.execution.macros

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.intellij.ide.macro.Macro
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class GdbServerMacro : Macro() {
    override fun getName() = "WokwiGdbServer"

    override fun getDescription() = "Resolves to the Wokwi's GDB Server address"

    override fun expand(dataContext: DataContext): String? {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null
        val config = runBlocking(Dispatchers.IO) { WokwiConfigProcessor.readConfig(project) } ?: return null
        val wokwiService = project.service<WokwiProjectService>()

        val port = wokwiService.getRunningGDBPort() ?: config.gdbServerPort ?: return "localhost:<unknown-port>"

        return "localhost:$port"
    }
}