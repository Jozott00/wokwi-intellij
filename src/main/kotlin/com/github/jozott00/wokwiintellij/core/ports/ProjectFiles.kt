package com.github.jozott00.wokwiintellij.core.ports

import java.nio.file.Path

/**
 * Reads project files needed while preparing a simulator session.
 *
 * Implementations own host-specific file access details. Core and session-preparation code should depend on this port
 * instead of reaching directly into IntelliJ project or virtual-file APIs.
 */
interface ProjectFiles {
    fun exists(path: Path): Boolean

    fun readBytes(path: Path): ByteArray

    fun readString(path: Path): String
}
