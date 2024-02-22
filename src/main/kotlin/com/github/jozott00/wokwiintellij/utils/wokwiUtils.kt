package com.github.jozott00.wokwiintellij.utils

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

fun Path.resolveWith(project: Project): Path? {
    val basePath = project.basePath ?: return null
    return Path(basePath).resolve(this)
}

