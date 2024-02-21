package com.github.jozott00.wokwiintellij.toml.inspections

import com.github.jozott00.wokwiintellij.WokwiBundle
import com.github.jozott00.wokwiintellij.toml.stringValue
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.psi.PsiElementVisitor
import com.intellij.testFramework.utils.vfs.getDirectory
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import kotlin.io.path.pathString

class ElfFirmwareInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : WokwiConfigVisitor() {
            override fun visitElfValue(value: TomlKeyValue) {
                super.visitElfValue(value)
                inspectBinaryPath(value)
            }

            override fun visitFirmwareValue(value: TomlKeyValue) {
                super.visitFirmwareValue(value)
                inspectBinaryPath(value)
            }

            private fun inspectBinaryPath(value: TomlKeyValue) {
                val path = value.value?.stringValue ?: run {
                    holder.registerProblem(
                        value,
                        WokwiBundle.message("config.inspection.binary.invalid.string.descriptor")
                    )
                    return
                }

                val configRootDir = holder.file.virtualFile.parent
                val filePath = configRootDir.toNioPath().resolve(path)
                val file = LocalFileSystem.getInstance().findFileByNioFile(filePath)
                if (file == null || file.isDirectory) {
                    holder.registerProblem(
                        value,
                        WokwiBundle.message("config.inspection.binary.invalid.path.descriptor", file?.path.toString())
                    )
                    return
                }
            }

        }
    }


}
