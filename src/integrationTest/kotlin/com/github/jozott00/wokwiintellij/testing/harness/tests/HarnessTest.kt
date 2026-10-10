package com.github.jozott00.wokwiintellij.testing.harness.tests

import com.github.jozott00.wokwiintellij.testing.fixtures.Fixtures
import com.github.jozott00.wokwiintellij.testing.harness.ConsoleCheckpoint
import com.github.jozott00.wokwiintellij.testing.harness.ConsoleSnapshot
import com.github.jozott00.wokwiintellij.testing.harness.ProjectFixture
import com.github.jozott00.wokwiintellij.testing.harness.RunConsole
import com.github.jozott00.wokwiintellij.testing.harness.TestDiagnostics
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

/** Offline checks for false-positive prevention and fixture integrity; no IDE or license is required. */
@Tag("harness")
class HarnessTest {
    /** Temporary destinations prove fixture edits cannot leak back into committed test data. */
    @TempDir lateinit var temporary: Path

    /** UART prints can arrive in several writes; assertions must match the accumulated document. */
    @Test fun `matches fragmented console output`() {
        val observations = ArrayDeque(listOf(snapshot("HEX sim"), snapshot("HEX simulation ready\n")))
        RunConsole { observations.removeFirst() }.awaitText("HEX simulation ready\n", 500.milliseconds)
    }

    /** Reusing a process must not allow output emitted before a checkpoint to satisfy a new assertion. */
    @Test fun `old output cannot satisfy checkpoint assertion`() {
        val console = RunConsole { snapshot("ready\n") }
        val checkpoint = console.checkpoint()
        assertThrows(AssertionError::class.java) { console.awaitText("ready", 1.milliseconds, checkpoint) }
    }

    /** A fresh descriptor or cleared console has its own output coordinate space. */
    @Test fun `new execution and replaced document can satisfy checkpoint assertion`() {
        val checkpoint = ConsoleCheckpoint(1, "old ready\n")
        RunConsole { snapshot("ready\n", id = 2) }.awaitText("ready", after = checkpoint)
        RunConsole { snapshot("ready\n") }.awaitText("ready", after = checkpoint)
    }

    /** Restart in the same execution must wait for prints appended after its checkpoint. */
    @Test fun `appended output satisfies checkpoint assertion`() {
        RunConsole { snapshot("ready\nready\n") }.awaitText("ready", after = ConsoleCheckpoint(1, "ready\n"))
    }

    /** Simulator failure should surface immediately instead of consuming the full output deadline. */
    @Test fun `premature termination reports exit and console tail`() {
        val console = RunConsole { snapshot("load failed", terminated = true) }
        val error = assertThrows(IllegalStateException::class.java) { console.awaitText("ready") }
        assertTrue(error.message!!.contains("load failed"))
        assertTrue(error.message!!.contains("exited (1)"))
    }

    /** Every committed simulator fixture must pass its manifest check before an IDE is launched. */
    @Test fun `committed simulator fixtures verify and copy`() {
        val repository = Path.of(System.getProperty("wokwi.test.projectRoot"))
        for (fixture in listOf(Fixtures.avrHex, Fixtures.customChip)) {
            val copy = fixture.copyTo(repository, temporary.resolve(fixture.name))
            assertArrayEquals(
                Files.readAllBytes(repository.resolve(fixture.relativePath).resolve("firmware.hex")),
                Files.readAllBytes(copy.resolve("firmware.hex")),
            )
        }
    }

    /** Corrupted firmware must be rejected before IDE startup, while mutations stay within the copy. */
    @Test fun `fixture copy is isolated and rejects corrupt firmware`() {
        val repository = Path.of(System.getProperty("wokwi.test.projectRoot"))
        val copy = Fixtures.avrHex.copyTo(repository, temporary.resolve("copy"))
        copy.resolve("firmware.hex").writeText(":corrupted")
        assertNotEquals(":corrupted", repository.resolve(Fixtures.avrHex.relativePath).resolve("firmware.hex").readText())
        assertThrows(IllegalArgumentException::class.java) {
            ProjectFixture("corrupt", "copy").copyTo(temporary, temporary.resolve("rejected"))
        }
    }

    /** Local SDK/indexing settings and undeclared firmware must not affect a reproducible fixture run. */
    @Test fun `fixture copy excludes undeclared local files`() {
        val repository = Path.of(System.getProperty("wokwi.test.projectRoot"))
        val source = Fixtures.avrHex.copyTo(repository, temporary.resolve("source"))
        Files.createDirectories(source.resolve(".idea"))
        source.resolve(".idea/misc.xml").writeText("local-sdk-settings")
        source.resolve("untracked.hex").writeText("local-firmware")
        val copy = ProjectFixture("local-files", "source").copyTo(temporary, temporary.resolve("clean"))
        assertFalse(Files.exists(copy.resolve(".idea")))
        assertFalse(Files.exists(copy.resolve("untracked.hex")))
        assertEquals(source.resolve("firmware.hex").readText(), copy.resolve("firmware.hex").readText())
    }

    /** Exported diagnostics must never contain the runtime credential. */
    @Test fun `diagnostics redact credentials`() {
        TestDiagnostics(temporary, "test-secret").write("console.txt", "before test-secret after")
        assertEquals("before [REDACTED] after", temporary.resolve("console.txt").readText())
    }

    /** Build compact observations for failure/race tests without simulating IDE or UART behavior. */
    private fun snapshot(text: String, id: Long = 1, terminated: Boolean = false) =
        ConsoleSnapshot(id, text, terminated, if (terminated) 1 else null)
}
