package com.github.jozott00.wokwiintellij.ide.services

import com.github.jozott00.wokwiintellij.core.ports.ProjectFiles
import java.nio.file.Files
import java.nio.file.Path

/**
 * Filesystem-backed project file access for IntelliJ project adapters.
 */
object IntelliJProjectFiles : ProjectFiles {
    override fun exists(path: Path): Boolean = Files.exists(path)

    override fun readBytes(path: Path): ByteArray = Files.readAllBytes(path)

    override fun readString(path: Path): String = Files.readString(path)
}
