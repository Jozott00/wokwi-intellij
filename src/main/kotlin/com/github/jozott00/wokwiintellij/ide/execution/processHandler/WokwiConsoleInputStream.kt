package com.github.jozott00.wokwiintellij.ide.execution.processHandler

import java.io.IOException
import java.io.OutputStream
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean

/** Forwards the IDE's encoded console bytes without decoding or changing line endings. */
internal class WokwiConsoleInputStream(private val send: (ByteArray) -> Boolean) : OutputStream() {
    private val closed = AtomicBoolean()

    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        Objects.checkFromIndexSize(offset, length, bytes.size)
        if (closed.get()) throw IOException("Wokwi console input is closed")
        if (length == 0) return
        if (!send(bytes.copyOfRange(offset, offset + length))) {
            throw IOException("Wokwi simulator is not ready for console input")
        }
    }

    override fun close() {
        closed.set(true)
    }
}
