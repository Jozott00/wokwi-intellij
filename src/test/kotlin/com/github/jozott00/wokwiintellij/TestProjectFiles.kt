package com.github.jozott00.wokwiintellij

import com.github.jozott00.wokwiintellij.core.ports.ProjectFiles
import java.nio.file.Path

class TestProjectFiles(
    val projectDir: Path = Path.of("/project"),
    files: Map<Path, ByteArray> = emptyMap(),
) : ProjectFiles {
    private val files = files.mapKeys { resolve(it.key) }.toMutableMap()

    fun addFile(projectRelativePath: String, content: String = ""): Path =
        addFile(Path.of(projectRelativePath), content.encodeToByteArray())

    fun addFile(projectRelativePath: String, bytes: ByteArray): Path =
        addFile(Path.of(projectRelativePath), bytes)

    fun addFile(path: Path, content: String = ""): Path =
        addFile(path, content.encodeToByteArray())

    fun addFile(path: Path, bytes: ByteArray): Path {
        val resolvedPath = resolve(path)
        files[resolvedPath] = bytes
        return resolvedPath
    }

    fun path(projectRelativePath: String): Path = resolve(Path.of(projectRelativePath))

    override fun exists(path: Path): Boolean = files.containsKey(resolve(path))

    override fun readBytes(path: Path): ByteArray = files.getValue(resolve(path))

    override fun readString(path: Path): String = readBytes(path).decodeToString()

    private fun resolve(path: Path): Path {
        val normalized = path.normalize()
        return if (normalized.isAbsolute) {
            normalized
        } else {
            projectDir.resolve(normalized).normalize()
        }
    }
}
