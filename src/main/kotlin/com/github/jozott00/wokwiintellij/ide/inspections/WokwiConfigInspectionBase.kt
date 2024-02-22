package com.github.jozott00.wokwiintellij.ide.inspections

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.ide.WokwiFileType
import com.github.jozott00.wokwiintellij.toml.isWokwiToml
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

abstract class WokwiConfigInspectionBase: LocalInspectionTool() {

    final override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean
    ): Array<ProblemDescriptor>? {
        if (!file.isWokwiToml) return super.checkFile(file, manager, isOnTheFly)
        return checkFileInternal(file, manager, isOnTheFly) ?: super.checkFile(file, manager, isOnTheFly)
    }

    protected open fun checkFileInternal(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? = null

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isWokwiToml) return super.buildVisitor(holder, isOnTheFly)
        return buildVisitorInternal(holder, isOnTheFly) ?: super.buildVisitor(holder, isOnTheFly)
    }

    protected open fun buildVisitorInternal(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? = null

}