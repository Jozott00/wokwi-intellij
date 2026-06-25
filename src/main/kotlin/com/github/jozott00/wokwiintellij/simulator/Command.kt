package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Suppress("unused")
object Command {
    fun start(diagram: String, firmware: String, firmwareFormat: FirmwareFormat, license: String, waitForDebugger: Boolean, chips: JsonElement): String =
        Json.encodeToString(buildJsonObject {
            put("command", "start")
            put("diagram", diagram)
            put("license", license)
            put("firmware", firmware)
            put("firmwareFormat", firmwareFormat.toString())
            put("firmwareB64", true)
            put("chips", chips)
            put("pause", waitForDebugger)
            put("useGateway", false) // private gateways not yet supported
            put("disableSerialMonitor", true)
        }
    )

    fun editor(diagram: String, license: String) = Json.encodeToString(
        buildJsonObject {
            put("command", "editor")
            put("diagram", diagram)
            put("license", license)
            put("chips", Json.parseToJsonElement("[]"))
            put("readonly", false)
        }
    )

    fun resourceData(buffer: String): String = Json.encodeToString(
        buildJsonObject {
            put("command", "resourceData")
            put("buffer", buffer)
        }
    )

    fun gdbMessage(message: String): String = Json.encodeToString(
        buildJsonObject {
            put("command", "gdbMessage")
            put("message", message)
        }
    )

    fun gdbBreak(): String = Json.encodeToString(
        buildJsonObject {
            put("command", "gdbBreak")
        }
    )

}