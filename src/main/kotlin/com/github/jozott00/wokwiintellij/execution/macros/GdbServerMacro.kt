package com.github.jozott00.wokwiintellij.execution.macros

import com.github.jozott00.wokwiintellij.services.WokwiSimulatorService
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
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
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: throw ExecutionCancelledException()
        val simulatorService = project.service<WokwiSimulatorService>()

        // only get config file if simulator currently running.
        // prevents ui freezing on startup
        if (!simulatorService.isSimulatorRunning()) return "<requires simulator startup>"

//        val config = runBlocking(Dispatchers.IO) { WokwiConfigProcessor.readConfig(project) }
//            ?: throw ExecutionCancelledException()
//        val port = simulatorService.getRunningGDBPort() ?: config.gdbServerPort ?: return "localhost:<unknown-port>"
        val port = simulatorService.getRunningGDBPort() ?: return "localhost:<unknown-port>"

        return "localhost:$port"
    }
}