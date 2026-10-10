package com.github.jozott00.wokwiintellij.ide.execution.processHandler

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WokwiConsoleInputStreamTest {
    @Test
    fun `bulk input preserves UTF8 and newlines in a single write`() {
        val received = mutableListOf<ByteArray>()
        val stream = WokwiConsoleInputStream { received.add(it); true }
        val input = "Grüße\r\n".encodeToByteArray()
        stream.write(input)
        assertContentEquals(input, received.single())
    }

    @Test
    fun `bulk input copies only the selected bytes`() {
        val received = mutableListOf<ByteArray>()
        val stream = WokwiConsoleInputStream { received.add(it); true }
        val input = byteArrayOf(0, 65, -128, -1, 10, 0)
        stream.write(input, 1, 4)
        input.fill(0)
        assertContentEquals(byteArrayOf(65, -128, -1, 10), received.single())
    }

    @Test
    fun `single writes use the low eight bits`() {
        val received = mutableListOf<ByteArray>()
        val stream = WokwiConsoleInputStream { received.add(it); true }
        stream.write(0x1ff)
        stream.write(0x180)
        assertEquals(2, received.size)
        assertContentEquals(byteArrayOf(-1), received[0])
        assertContentEquals(byteArrayOf(-128), received[1])
    }

    @Test
    fun `empty writes do not send UART messages`() {
        val stream = WokwiConsoleInputStream { error("Empty input must not be sent") }
        stream.write(ByteArray(0))
        stream.write(byteArrayOf(1), 1, 0)
    }

    @Test
    fun `invalid byte ranges never reach the simulator`() {
        val stream = WokwiConsoleInputStream { error("Invalid input must not be sent") }
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 2 to 0, 1 to 1, 1 to Int.MAX_VALUE)) {
            assertFailsWith<IndexOutOfBoundsException> { stream.write(byteArrayOf(1), offset, length) }
        }
    }

    @Test
    fun `unavailable simulator reports an input error`() {
        var calls = 0
        val stream = WokwiConsoleInputStream { calls++; false }
        assertFailsWith<IOException> { stream.write(65) }
        assertEquals(1, calls)
    }

    @Test
    fun `closing the stream prevents further sends`() {
        val received = mutableListOf<ByteArray>()
        val stream = WokwiConsoleInputStream { received.add(it); true }
        stream.close()
        stream.close()
        assertFailsWith<IOException> { stream.write(65) }
        assertFailsWith<IOException> { stream.write(byteArrayOf(65, 10)) }
        assertTrue(received.isEmpty())
    }
}
