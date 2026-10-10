package com.github.jozott00.wokwiintellij.testing.harness

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.StepsProvider
import com.intellij.driver.sdk.ui.Finder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.TimeSource

/** Driver's ServiceLoader extension for timestamped steps, including SDK actions and UI operations.
 * Uses the test worker's local clock, matching Starter logs, and a monotonic clock for elapsed time.
 * Failure messages omit exception details because remote exceptions can contain credential arguments.
 */
class TimestampedStepsProvider : StepsProvider {
    /** Log a standalone SDK or harness step and preserve its result or original exception. */
    override fun <T> step(name: String, action: () -> T): T = logStep(name, action)

    /** Apply the same logging to steps invoked in a Driver context. */
    override fun <T> Driver.step(name: String, action: () -> T): T = logStep(name, action)

    /** Apply the same logging to UI Finder operations. */
    override fun <T> Finder.step(name: String, action: () -> T): T = logStep(name, action)

    /** Report both successful and failed step durations without changing execution semantics. */
    private fun <T> logStep(name: String, action: () -> T): T {
        val started = TimeSource.Monotonic.markNow()
        startStep(name)
        return try {
            action().also { println("finished (${started.elapsedNow().inWholeMilliseconds} ms)") }
        } catch (error: Throwable) {
            println("failed (${started.elapsedNow().inWholeMilliseconds} ms)")
            throw error
        }
    }

    /** Flush a timestamped prefix immediately; completion appends the status and terminates the line. */
    private fun startStep(name: String) {
        print("[${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))}]: Step '$name' ... ")
        System.out.flush()
    }
}
