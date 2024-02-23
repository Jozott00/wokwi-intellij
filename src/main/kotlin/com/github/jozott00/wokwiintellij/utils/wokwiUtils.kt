package com.github.jozott00.wokwiintellij.utils

import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.Path

fun Path.resolveWith(project: Project): Path? {
    val basePath = project.basePath ?: return null
    return Path(basePath).resolve(this)
}

