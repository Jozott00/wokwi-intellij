package com.github.jozott00.wokwiintellij.execution.macros

import com.github.jozott00.wokwiintellij.extensions.wokwiCoroutineChildScope
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.intellij.ide.macro.Macro
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class ElfPathMacro : Macro() {
    override fun getName() = "WokwiElfPath"

    override fun getDescription() = "Resolves to the ELF file path specified in the Wokwi configuration."

    override fun expand(dataContext: DataContext): String {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return "no-project"

        if (project.isDisposed || !project.isInitialized) {
            return "project-not-ready"
        }

        return runBlocking {
            project.wokwiCoroutineChildScope("ElfPathMacro").async(Dispatchers.IO) {
                WokwiConfigProcessor.findElfFile(project)
            }.await()
        }?.path ?: "no-elf-found"
    }
}