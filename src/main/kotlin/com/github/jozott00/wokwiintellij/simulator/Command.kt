package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.core.protocol.GdbBreakPayload
import com.github.jozott00.wokwiintellij.core.protocol.GdbMessagePayload
import com.github.jozott00.wokwiintellij.core.protocol.ResourceDataPayload
import com.github.jozott00.wokwiintellij.core.protocol.SimulatorStartPayload
import com.github.jozott00.wokwiintellij.core.protocol.WokwiProtocolCodec
import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat

@Suppress("unused")
object Command {

    fun start(diagram: String, firmware: String, firmwareFormat: FirmwareFormat, license: String, waitForDebugger: Boolean): String {
        return WokwiProtocolCodec.encode(
            SimulatorStartPayload(
                diagram = diagram,
                firmware = firmware,
                firmwareFormat = firmwareFormat.toString(),
                license = license,
                pause = waitForDebugger,
            )
        )
    }

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
