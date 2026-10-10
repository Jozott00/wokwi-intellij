package com.github.jozott00.wokwiintellij.testing.fixtures

import com.github.jozott00.wokwiintellij.testing.harness.ProjectFixture

/** Named project inputs used by feature tests; keep concrete definitions outside the reusable harness. */
object Fixtures {
    /** ATmega328P UART firmware in Intel HEX format, covering the browser's HEX decoding path. */
    val avrHex = ProjectFixture("avr-hex", "testData/simulator/avr-hex")

    /** WASM custom chip printing to the chip console alongside silent ATmega328P firmware. */
    val customChip = ProjectFixture("custom-chip", "testData/simulator/custom-chip")
}
