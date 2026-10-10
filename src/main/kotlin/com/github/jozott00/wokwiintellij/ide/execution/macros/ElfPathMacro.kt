package com.github.jozott00.wokwiintellij.ide.execution.macros

import com.github.jozott00.wokwiintellij.ide.config.WokwiProjectConfigResolver
import com.intellij.ide.macro.Macro
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ElfPathMacro : Macro() {
    override fun getName() = "WokwiElfPath"

    override fun getDescription() = "Resolves to the ELF file path specified in the Wokwi configuration."

    override fun expand(dataContext: DataContext): String {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return "no-project"

        if (project.isDisposed || !project.isInitialized) {
            return "project-not-ready"
        }

        return runBlockingCancellable {
            withContext(Dispatchers.IO) {
                WokwiProjectConfigResolver(project).findElfFile()
            }
        }?.path ?: "no-elf-found"
    }
}
