package com.github.jozott00.wokwiintellij.testing

import com.github.jozott00.wokwiintellij.testing.fixtures.Fixtures
import com.github.jozott00.wokwiintellij.testing.harness.prepareClionRemoteDebug
import com.github.jozott00.wokwiintellij.testing.harness.runWokwiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.io.path.readLines

/** Real CLion Remote Debug coverage using the macros and before-launch task from the user guide. */
@Tag("live-wokwi")
@Tag("clion-debugger")
class ClionDebuggerTest {
    /** Prove attachment, source mapping, breakpoint suspension, variable reads, stepping and firmware continuation. */
    @Test fun `remote debugger hits a breakpoint and steps through firmware`() =
        runWokwiTest(Fixtures.avrUart, prepareProject = ::prepareClionRemoteDebug) {
            val source = projectDirectory.resolve("main.c")
            val lines = source.readLines()
            val breakpoint = lines.indexOfFirst { "DEBUG_BREAKPOINT" in it } + 1
            val nextLine = lines.indexOfFirst { "DEBUG_STEP" in it } + 1
            debugger.setBreakpoint(source, breakpoint)
            debugger.launch()
            debugger.awaitSuspendedAt(source, breakpoint)
            debugger.awaitVariable("counter", 41)
            debugger.stepOver()
            debugger.awaitSuspendedAt(source, nextLine)
            debugger.awaitVariable("counter", 42)
            // Debug may hide Wokwi; keep its browser rendering before firmware continuation.
            simulator.ensureToolWindowOpen()
            debugger.resume()
            console.awaitText("AVR simulation ready\n")
            debugger.stopAndAwaitTermination()
            simulator.stopAndAwaitTermination()
        }
}
