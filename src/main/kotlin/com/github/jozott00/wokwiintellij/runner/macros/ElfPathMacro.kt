package com.github.jozott00.wokwiintellij.runner.macros

import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.intellij.ide.macro.Macro
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import kotlinx.coroutines.runBlocking

class ElfPathMacro : Macro() {
    override fun getName() = "WokwiElfPath"

    override fun getDescription() = "Resolves to the ELF file path specified in the Wokwi configuration."

    override fun expand(dataContext: DataContext): String? {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null
        val projectSettings = project.service<WokwiSettingsState>()
        val config = runBlocking {
            WokwiConfigProcessor.loadConfig(
                project,
                projectSettings.wokwiConfigPath,
                projectSettings.wokwiDiagramPath
            )
        } ?: return null

        return config.elf.path
    }
}