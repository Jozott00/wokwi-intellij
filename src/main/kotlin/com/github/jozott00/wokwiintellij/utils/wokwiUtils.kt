package com.github.jozott00.wokwiintellij.utils

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

fun Path.resolveWith(project: Project): Path? {
    val basePath = project.basePath ?: return null
    return Path(basePath).resolve(this)
}

fun Project.wokwiCoroutineChildScope(childName: String): CoroutineScope {
    return service<WokwiProjectService>().childScope(childName)
}

fun Project.findRelativePaths(path: String): List<Path> {
    val rootUrls = ProjectRootManager.getInstance(this).contentRootUrls
    return rootUrls.map {
            Path.of(URI(it)).resolve(path)
        }.filter {
            it.exists()
        }
    }

fun Project.findRelativeFiles(path: String): List<VirtualFile> {
    val rootUrls = ProjectRootManager.getInstance(this).contentRoots
    return rootUrls.mapNotNull {
        it.findFileByRelativePath(path)
    }
}
