package com.github.jozott00.wokwiintellij.toml

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

val PsiFile.isWokwiToml: Boolean get() = name == WokwiConstants.WOKWI_CONFIG_FILE

val TomlKey.stringValue: String
    get() {
        return segments.map { it.name }.joinToString(".")
    }

val TomlValue.stringValue: String?
    get() {
        val kind = (this as? TomlLiteral)?.kind
        return (kind as? TomlLiteralKind.String)?.value
    }

val TomlFile.tableList: List<TomlTable> get() = childrenOfType<TomlTable>()


fun TomlFile.findTable(key: String): TomlTable? {
    return tableList.find {
        it.header.key?.stringValue == key
    }
}

fun TomlKeyValueOwner.findValue(key: String): TomlValue? {
    return entries.find { it.key.stringValue == key }?.value
}
