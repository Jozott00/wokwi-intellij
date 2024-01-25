package com.github.jozott00.wokwiintellij.toml.inspections

import com.github.jozott00.wokwiintellij.WokwiBundle
import com.github.jozott00.wokwiintellij.WokwiConstants
import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.toml.lang.psi.*

class ConfigVersionInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : WokwiConfigVisitor() {
            override fun visitVersionValue(value: TomlKeyValue) {
                super.visitVersionValue(value)

                if (value.value?.text != "1") {
                    holder.registerProblem(
                        value,
                        WokwiBundle.message("config.inspection.version.invalid.problem.descriptor"),
                        SetValidVersionQuickFix
                    )
                }
            }
        }
    }


    object SetValidVersionQuickFix : LocalQuickFix {
        override fun getFamilyName(): String {
            return WokwiBundle.message(
                "config.inspection.version.invalid.quickfix.change",
                WokwiConstants.WOKWI_DEFAULT_CONFIG_VERSION
            )
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val elem = descriptor.psiElement as TomlKeyValue

            val newElem = TomlPsiFactory(project, true)
                .createKeyValue("version", WokwiConstants.WOKWI_DEFAULT_CONFIG_VERSION)
            elem.replace(newElem)
        }

    }
}
