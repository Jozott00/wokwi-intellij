package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON-based structure for data exchange and communication with Wokwi's backend layer.
 */
@Suppress("unused")
object Command {
    /**
     * Builds a JSON command for simulation start request, packaging wokwi configuration data.
     * @param diagram diagram.json file content string
     * @param firmware Compiled board firmware (Base64 string).
     * @param firmwareFormat Firmware compilation format.
     * @param license Wokwi plugin access license data.
     * @param waitForDebugger Boolean flag to instruct the simulator to wait or not for the debugger to fully start.
     * @param chips Chip configuration JSON data.
     */
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

    /**
     * Builds a JSON command for editor start request, packaging wokwi configuration data.
     * @param diagram diagram.json file content string.
     * @param license Wokwi plugin access license data.
     */
    fun editor(diagram: String, license: String) = Json.encodeToString(
        buildJsonObject {
            put("command", "editor")
            put("diagram", diagram)
            put("license", license)
            put("chips", Json.parseToJsonElement("[]"))
            put("readonly", false)
        }
    )

    /**
     * Builds a JSON command for resource data send, packaging resource's content in a buffer.
     * @param buffer Resource's content in string buffer format.
     */
    fun resourceData(buffer: String): String = Json.encodeToString(
        buildJsonObject {
            put("command", "resourceData")
            put("buffer", buffer)
        }
    )

    /**
     * Builds a JSON command for gdb communication, packaging the outgoing message in string format.
     * @param message Message content string.
     */
    fun gdbMessage(message: String): String = Json.encodeToString(
        buildJsonObject {
            put("command", "gdbMessage")
            put("message", message)
        }
    )

    /**
     * Builds a JSON command for pausing the gdb server process.
     */
    fun gdbBreak(): String = Json.encodeToString(
        buildJsonObject {
            put("command", "gdbBreak")
        }
    )
}