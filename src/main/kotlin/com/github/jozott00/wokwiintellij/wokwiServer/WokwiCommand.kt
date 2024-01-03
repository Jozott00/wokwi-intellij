package com.github.jozott00.wokwiintellij.wokwiServer

import espimg.RomSegment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.*


@Serializable
data class WokwiCommand(
    val type: String,
    val elf: String,
    val espBin: List<List<JsonElement>>
) {
    companion object {
        fun start(bytes: ByteArray, segments: List<RomSegment>): WokwiCommand {
            val bootloader = segments[0]
            val partitionTable = segments[1]
            val app = segments[2]

            return WokwiCommand(
                type = "start",
                elf = bytes.toBase64(),
                espBin = listOf(
                    romSegToVec(bootloader),
                    romSegToVec(partitionTable),
                    romSegToVec(app),
                )
            )
        }

        private fun romSegToVec(romSeg: RomSegment): List<JsonPrimitive> {
            return listOf(
                JsonPrimitive(romSeg.addr),
                JsonPrimitive(romSeg.data.toBase64()),
            )
        }
    }
}

private fun ByteArray.toBase64(): String =
    String(Base64.getEncoder().encode(this))



