package com.github.jozott00.wokwiintellij.core.firmware

import com.github.jozott00.wokwiintellij.core.model.FirmwareFormat
import java.nio.file.Path

object FirmwareFormatDetector {
    private val uf2FirstMagic = byteArrayOf(0x0A, 0x32, 0x46, 0x55)
    private val uf2SecondMagic = byteArrayOf(0x9E.toByte(), 0x5D, 0x51, 0x57)
    private val uf2FinalMagic = byteArrayOf(0x0A, 0xB1.toByte(), 0x6F, 0x30)

    fun detect(filePath: Path, content: ByteArray): FirmwareFormat {
        if (content.size >= 512 && isUf2Block(content.sliceArray(0 until 512))) {
            return FirmwareFormat.UF2
        }

        return when (filePath.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "hex" -> FirmwareFormat.HEX
            else -> FirmwareFormat.BIN
        }
    }

    private fun isUf2Block(block: ByteArray): Boolean {
        if (block.size != 512) return false

        val firstMagicNumber = block.sliceArray(0 until 4)
        if (!firstMagicNumber.contentEquals(uf2FirstMagic)) return false

        val secondMagicNumber = block.sliceArray(4 until 8)
        if (!secondMagicNumber.contentEquals(uf2SecondMagic)) return false

        val finalMagicNumber = block.sliceArray(block.size - 4 until block.size)
        return finalMagicNumber.contentEquals(uf2FinalMagic)
    }
}
