package com.github.jozott00.wokwiintellij.simulator

import com.beust.klaxon.json
import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat

@Suppress("unused")
object Command {

    fun start(diagram: String, firmware: String, firmwareFormat: FirmwareFormat, license: String, waitForDebugger: Boolean): String {
        return json {
            obj(
                "command" to "start",
                "diagram" to diagram,
                "license" to license,
                "firmware" to firmware,
                "firmwareFormat" to firmwareFormat.toString(),
                "firmwareB64" to true,
                "pause" to waitForDebugger,
                "useGateway" to false, // private gateways not yet supported
                "disableSerialMonitor" to true,
            )
        }.toJsonString()
    }

    fun editor(diagram: String, license: String) = json {
        obj(
            "command" to "editor",
            "diagram" to diagram,
            "license" to license,
            "chips" to array(),
            "readonly" to false,
        )
    }.toJsonString()


    fun resourceData(buffer: String): String {
        return json {
            obj(
                "command" to "resourceData",
                "buffer" to buffer,
            )
        }.toJsonString()
    }

    fun gdbMessage(message: String): String {
        return json {
            obj(
                "command" to "gdbMessage",
                "message" to message,
            )
        }.toJsonString()
    }

    fun gdbBreak(): String {
        return json {
            obj(
                "command" to "gdbBreak",
            )
        }.toJsonString()
    }

}