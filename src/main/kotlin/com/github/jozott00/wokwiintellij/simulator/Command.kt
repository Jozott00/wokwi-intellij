package com.github.jozott00.wokwiintellij.simulator

import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonJson
import com.beust.klaxon.json

object Command {

    fun start(diagram: String, firmware: String, license: String): String {
        return json {
            obj(
                "command" to "start",
                "diagram" to diagram,
                "license" to license,
                "firmware" to firmware,
                "firmwareB64" to true,
                "pause" to false,
                "useGateway" to false, // private gateways not yet supported
                "disableSerialMonitor" to false,
            )
        }.toJsonString()
    }

    fun editor(diagram: String, license: String): String {
        return json {
            obj(
                "command" to "editor",
                "diagram" to diagram,
                "license" to license,
                "chips" to array(),
                "readonly" to false,
            )
        }.toJsonString()
    }

    fun resourceData(buffer: String): String {
        return json {
            obj(
                "command" to "resourceData",
                "buffer" to buffer,
            )
        }.toJsonString()
    }
}