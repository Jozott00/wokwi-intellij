package com.github.jozott00.wokwiintellij.utils.simulation

import com.github.jozott00.wokwiintellij.core.model.FirmwareFormat
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class FirmwareUtilsTest {

    @Test
    fun `detects uf2 by block magic`() {
        val uf2 = ByteArray(512)
        byteArrayOf(0x0A, 0x32, 0x46, 0x55).copyInto(uf2, 0)
        byteArrayOf(0x9E.toByte(), 0x5D, 0x51, 0x57).copyInto(uf2, 4)
        byteArrayOf(0x0A, 0xB1.toByte(), 0x6F, 0x30).copyInto(uf2, 508)

        assertEquals(FirmwareFormat.UF2, FirmwareUtils.determineFirmwareFormat(Path.of("/tmp/firmware.bin"), uf2))
    }

    @Test
    fun `detects hex by extension`() {
        assertEquals(FirmwareFormat.HEX, FirmwareUtils.determineFirmwareFormat(Path.of("/tmp/firmware.hex"), byteArrayOf(1, 2)))
    }

    @Test
    fun `defaults to bin`() {
        assertEquals(FirmwareFormat.BIN, FirmwareUtils.determineFirmwareFormat(Path.of("/tmp/firmware.elf"), byteArrayOf(1, 2)))
    }
}
