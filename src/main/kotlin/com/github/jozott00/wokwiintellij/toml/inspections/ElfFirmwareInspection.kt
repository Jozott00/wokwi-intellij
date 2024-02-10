package com.github.jozott00.wokwiintellij.toml.inspections

import com.github.jozott00.wokwiintellij.WokwiBundle
import com.github.jozott00.wokwiintellij.toml.stringValue
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementVisitor
import org.toml.lang.psi.TomlKeyValue

class ElfFirmwareInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : WokwiConfigVisitor() {
            override fun visitElfValue(value: TomlKeyValue) {
                super.visitElfValue(value)

                val path = value.value?.stringValue ?: run {
                    holder.registerProblem(
                        value,
                        WokwiBundle.message("config.inspection.binary.invalid.string.descriptor")
                    )
                    return
                }

                resolveFile(path, holder.project) ?: run {
                    holder.registerProblem(
                        value.value!!,
                        WokwiBundle.message("config.inspection.binary.invalid.path.descriptor")
                    )
                    return
                }

            }

            override fun visitFirmwareValue(value: TomlKeyValue) {
                super.visitElfValue(value)

                val path = value.value?.stringValue ?: run {
                    holder.registerProblem(
                        value,
                        WokwiBundle.message("config.inspection.binary.invalid.string.descriptor")
                    )
                    return
                }

                val file = resolveFile(path, holder.project)
                if (file == null || file.isDirectory) {
                    holder.registerProblem(
                        value,
                        WokwiBundle.message("config.inspection.binary.invalid.path.descriptor")
                    )
                    return
                }


            }
        }
    }

    private fun resolveFile(pathString: String, project: Project): VirtualFile? {
        val trimmedPath = pathString.trim('"', ' ')
        val projectDir = project.guessProjectDir() ?: return null
        return projectDir.findFileByRelativePath(trimmedPath)
    }


}
