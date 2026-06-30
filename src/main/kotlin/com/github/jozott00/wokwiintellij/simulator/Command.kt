package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.core.protocol.GdbBreakPayload
import com.github.jozott00.wokwiintellij.core.protocol.GdbMessagePayload
import com.github.jozott00.wokwiintellij.core.protocol.ResourceDataPayload
import com.github.jozott00.wokwiintellij.core.protocol.SimulatorStartPayload
import com.github.jozott00.wokwiintellij.core.protocol.WokwiProtocolCodec
import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat
import kotlinx.serialization.json.*

@Suppress("unused")
object Command {

    fun start(diagram: String, firmware: String, firmwareFormat: FirmwareFormat, license: String, waitForDebugger: Boolean, chips: JsonElement): String {
        return WokwiProtocolCodec.encode(
            SimulatorStartPayload(
                diagram = diagram,
                firmware = firmware,
                firmwareFormat = firmwareFormat.toString(),
                license = license,
                pause = waitForDebugger,
                chips = chips.jsonArray.map { it.jsonObject },
            )
        )
    }

    fun editor(diagram: String, license: String) = Json.encodeToString(
        buildJsonObject {
            put("command", "editor")
            put("diagram", diagram)
            put("license", license)
            put("chips", Json.parseToJsonElement("[]"))
            put("readonly", false)
        }
    )

    fun resourceData(buffer: String): String {
        return WokwiProtocolCodec.encode(ResourceDataPayload(buffer = buffer))
    }

    fun gdbMessage(message: String): String {
        return WokwiProtocolCodec.encode(GdbMessagePayload(message = message))
    }

    fun gdbBreak(): String {
        return WokwiProtocolCodec.encode(GdbBreakPayload())
    }

}
