package com.github.jozott00.wokwiintellij.utils

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.io.path.Path

fun Path.resolveWith(project: Project): Path? {
    val basePath = project.basePath ?: return null
    return Path(basePath).resolve(this)
}

fun Project.wokwiCoroutineChildScope(childName: String): CoroutineScope {
    return service<WokwiProjectService>().childScope(childName)
}