package com.github.jozott00.wokwiintellij.extensions

import com.github.jozott00.wokwiintellij.services.WokwiPluginDisposable
import com.github.jozott00.wokwiintellij.services.WokwiSimulatorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists


/**
 * Represents a disposable object for the Wokwi plugin in a project.
 *
 * This property returns an instance of the `WokwiPluginDisposable` class, which is a service
 * intended to be used as a parent disposable instead of the project itself.
 */
val Project.wokwiDisposable get() = service<WokwiPluginDisposable>() as Disposable

/**
 * Creates a new CoroutineScope for the given childName in scope of the WokwiProjectService.
 *
 * @param childName the name of the child scope.
 * @return the created CoroutineScope for the specified childName.
 */
@Suppress("unused")
fun Project.wokwiCoroutineChildScope(childName: String): CoroutineScope {
    return service<WokwiSimulatorService>().childScope()
}

/**
 * Finds the relative paths of files or directories within the project.
 *
 * @param path the path to resolve against project's content roots
 * @return a list of resolved relative paths
 */
@Suppress("unused")
fun Project.findRelativePaths(path: String): List<Path> {
    val rootUrls = ProjectRootManager.getInstance(this).contentRootUrls
    return rootUrls.map {
        Path.of(URI(it)).resolve(path)
    }.filter {
        it.exists()
    }
}

/**
 * Finds the relative files based on the given path.
 *
 * @param path The relative path of the files to be found.
 * @return A list of virtual files matching the given relative path. If no files are found, returns an empty list.
 */
fun Project.findRelativeFiles(path: String): List<VirtualFile> {
    val rootUrls = ProjectRootManager.getInstance(this).contentRoots
    return rootUrls.mapNotNull {
        it.findFileByRelativePath(path)
    }
}