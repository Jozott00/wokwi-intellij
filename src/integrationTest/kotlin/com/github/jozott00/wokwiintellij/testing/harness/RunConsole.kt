package com.github.jozott00.wokwiintellij.testing.harness

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Immutable observation of the rendered Run console and its process, rather than a synthetic UART event. */
data class ConsoleSnapshot(val executionId: Long, val text: String, val terminated: Boolean, val exitCode: Int?)

/** Marks a run and its existing output, preventing earlier prints from satisfying later assertions. */
data class ConsoleCheckpoint(val executionId: Long, val prefix: String)

/** Bounded console assertions. The reader adapter keeps Driver/platform details out of feature tests. */
class RunConsole internal constructor(private val read: () -> ConsoleSnapshot?) {
    /** Read the current console; null means the execution descriptor has not been created yet. */
    fun snapshot(): ConsoleSnapshot? = read()

    /** Save the current execution and text position before an action expected to emit more output. */
    fun checkpoint(): ConsoleCheckpoint {
        val current = requireNotNull(snapshot()) { "No Wokwi Run console exists" }
        return ConsoleCheckpoint(current.executionId, current.text)
    }

    /** Wait for accumulated text (including split writes), failing promptly if the process exits. */
    fun awaitText(expected: String, timeout: Duration = 60.seconds, after: ConsoleCheckpoint? = null) {
        require(expected.isNotEmpty()) { "Expected output must not be empty" }
        var latest: ConsoleSnapshot? = null
        awaitCondition("Run console to contain ${expected.trim()}", timeout, { latest?.text?.takeLast(4000).orEmpty() }) {
            latest = snapshot()
            val current = latest ?: return@awaitCondition false
            val text = if (after != null && after.executionId == current.executionId && current.text.startsWith(after.prefix)) {
                current.text.drop(after.prefix.length)
            } else current.text
            if (text.contains(expected)) return@awaitCondition true
            check(!current.terminated) { "Simulator exited (${current.exitCode}) before expected output.\n${current.text.takeLast(4000)}" }
            false
        }
    }
}

/** Poll a condition with a monotonic deadline; diagnostic details are evaluated only on timeout. */
internal fun awaitCondition(description: String, timeout: Duration, details: () -> String = { "" }, condition: () -> Boolean) {
    require(timeout.isPositive()) { "Timeout must be positive" }
    val start = TimeSource.Monotonic.markNow()
    do {
        if (condition()) return
        if (start.elapsedNow() >= timeout) break
        Thread.sleep(100)
    } while (true)
    throw AssertionError("Timed out waiting for $description after $timeout.\n${details()}")
}
