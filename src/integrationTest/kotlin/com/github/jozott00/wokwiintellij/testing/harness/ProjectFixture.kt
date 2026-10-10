package com.github.jozott00.wokwiintellij.testing.harness

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readText

/** Committed simulator inputs, copied for each test so edits never change the original fixture. */
data class ProjectFixture(val name: String, val relativePath: String) {
    /** Verify and copy only pinned inputs plus their manifest; ignored local IDE settings never enter the project. */
    fun copyTo(repository: Path, destination: Path): Path {
        val source = repository.resolve(relativePath)
        val manifest = source.resolve("SHA256SUMS").readText().lineSequence().filter { it.isNotBlank() }.toList()
        require(manifest.isNotEmpty()) { "Empty fixture manifest: $name" }
        val inputs = manifest.map { line ->
            val fields = line.split(Regex("\\s+"), limit = 2)
            require(fields.size == 2 && fields[0].matches(Regex("[a-f0-9]{64}"))) { "Invalid fixture manifest: $name" }
            val file = source.resolve(fields[1]).normalize()
            require(file.startsWith(source) && Files.isRegularFile(file)) { "Missing fixture input: ${fields[1]}" }
            val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
                .joinToString("") { "%02x".format(it) }
            require(digest == fields[0]) { "Fixture checksum mismatch: ${fields[1]}" }
            fields[1]
        }
        require(inputs.containsAll(listOf("wokwi.toml", "diagram.json", "expected.txt"))) { "Incomplete fixture: $name" }
        require(source.resolve("expected.txt").readText().isNotBlank()) { "Empty expected output: $name" }
        (inputs + "SHA256SUMS").distinct().forEach { relativePath ->
            val output = destination.resolve(relativePath)
            Files.createDirectories(output.parent)
            Files.copy(source.resolve(relativePath), output)
        }
        return destination
    }
}
