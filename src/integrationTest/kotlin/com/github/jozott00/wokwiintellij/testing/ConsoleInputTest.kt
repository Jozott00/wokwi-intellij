package com.github.jozott00.wokwiintellij.testing

import com.github.jozott00.wokwiintellij.testing.fixtures.Fixtures
import com.github.jozott00.wokwiintellij.testing.harness.runWokwiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Exercises console typing, process input, JCEF UART delivery and actual firmware responses. */
@Tag("live-wokwi")
class ConsoleInputTest {
    @Test fun submittedLinesReachTheFirmware() = runWokwiTest(Fixtures.avrConsoleInput) {
        simulator.start()
        console.awaitText("Console input ready\n")
        for (line in listOf("hello Wokwi", "Grüße", "")) {
            val before = console.checkpoint()
            console.sendLine(line)
            console.awaitText("Received: $line\n", after = before)
        }
        simulator.stopAndAwaitTermination()
    }
}
