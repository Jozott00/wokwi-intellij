package com.github.jozott00.wokwiintellij.ide.inspections

import com.github.jozott00.wokwiintellij.WokwiBundle
import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.toml.findTable
import com.github.jozott00.wokwiintellij.toml.findValue
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInspection.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlTable

private fun InspectionManager.createErrorDescription(
    elem: PsiElement,
    descriptor: String,
    quickFix: LocalQuickFix?,
    type: ProblemHighlightType = ProblemHighlightType.ERROR
): ProblemDescriptor {
    return createProblemDescriptor(elem, descriptor, quickFix, type, false)
}

class MissingConfigurationInspection : WokwiConfigInspectionBase() {

    override fun checkFileInternal(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
        val tomlFile = file as TomlFile

        val wokwiTable = tomlFile.findTable("wokwi") ?: run {
            return arrayOf(
                manager.createErrorDescription(
                    tomlFile,
                    WokwiBundle.message("config.inspection.missing.wokwi.problem.descriptor"),
                    AddWokwiConfiguration
                )
            )
        }

        val problems = mutableListOf<ProblemDescriptor>()

        if (wokwiTable.findValue("version") == null) {
            problems.add(
                manager.createErrorDescription(
                    wokwiTable.header,
                    WokwiBundle.message("config.inspection.missing.version.problem.descriptor"),
                    AddWokwiAttribute(
                        wokwiTable,
                        "version",
                        WokwiConstants.WOKWI_DEFAULT_CONFIG_VERSION,
                        false,
                        WokwiBundle.message("config.inspection.missing.version.quickfix")
                    )
                )
            )
        }

        if (wokwiTable.findValue("elf") == null) {
            problems.add(
                manager.createErrorDescription(
                    wokwiTable.header,
                    WokwiBundle.message("config.inspection.missing.elf.problem.descriptor"),
                    AddWokwiAttribute(
                        wokwiTable,
                        "elf",
                        "path/to/your/elf",
                        true,
                        WokwiBundle.message("config.inspection.missing.elf.quickfix")
                    )
                )
            )
        }

        if (wokwiTable.findValue("firmware") == null) {
            problems.add(
                manager.createErrorDescription(
                    wokwiTable.header,
                    WokwiBundle.message("config.inspection.missing.firmware.problem.descriptor"),
                    AddWokwiAttribute(
                        wokwiTable,
                        "firmware",
                        "path/to/your/firmware",
                        true,
                        WokwiBundle.message("config.inspection.missing.firmware.quickfix"),
                    )
                )
            )
        }



        return problems.toTypedArray()
    }

    object AddWokwiConfiguration : LocalQuickFix {
        override fun getFamilyName(): String {
            return WokwiBundle.message("config.inspection.missing.wokwi.quickfix")
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as TomlFile
            val factory = TomlPsiFactory(project, true)
            val table = factory.createTable("wokwi")
            table.add(factory.createNewline())

            val version = factory.createKeyValue("version", WokwiConstants.WOKWI_DEFAULT_CONFIG_VERSION)
            table.add(version)

            file.add(table)

        }

    }

    class AddWokwiAttribute(
        private val wokwiTable: TomlTable,
        private val attribute: String,
        private val defaultValue: String,
        val runTemplate: Boolean,
        private val familyName: String,
    ) : LocalQuickFix {

        override fun getFamilyName(): String {
            return familyName
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val factory = TomlPsiFactory(project, true)

            wokwiTable.add(factory.createNewline())
            if (!runTemplate) {
                // if we do not want a template we just add the attribute
                val elem = factory.createKeyValue(attribute, defaultValue)
                wokwiTable.add(elem)
                return
            }



            WriteCommandAction.runWriteCommandAction(project) {
                // apply template
                val editor =
                    FileEditorManager.getInstance(project).selectedTextEditor ?: return@runWriteCommandAction

                PsiDocumentManager.getInstance(project)
                    .doPostponedOperationsAndUnblockDocument(editor.document)

                insertTemplate(project, editor)

            }


        }

        private fun insertTemplate(project: Project, editor: Editor) {
            val templateManager = TemplateManager.getInstance(project)
            val template = templateManager.createTemplate("", "", "$attribute = \"\$PATH$\"").apply {
                addVariable("PATH", TextExpression(defaultValue), true)
            }

            // Insert the template at end of wokwi table
            val caretModel = editor.caretModel
            val offset = wokwiTable.textRange.endOffset
            caretModel.moveToOffset(offset)

            templateManager.startTemplate(editor, template)
        }

    }


}
