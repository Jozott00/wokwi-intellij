package com.github.jozott00.wokwiintellij.testing

import com.github.jozott00.wokwiintellij.testing.fixtures.Fixtures
import com.github.jozott00.wokwiintellij.testing.harness.runWokwiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Exercises custom chip configuration, WASM/manifest loading, JCEF execution and Run-console forwarding. */
@Tag("live-wokwi")
class CustomChipTest {
    /** The marker comes from WASM chip_init through chipOutput; the AVR firmware produces no UART output. */
    @Test fun `custom chip executes and prints to the Run console`() = runWokwiTest(Fixtures.customChip) {
        simulator.start()
        console.awaitText("[chip-integration-printer] Custom chip simulation ready\n")
        simulator.stopAndAwaitTermination()
    }
}
