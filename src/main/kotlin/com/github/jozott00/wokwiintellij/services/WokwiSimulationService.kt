package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.jcef.BrowserPipe
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.github.jozott00.wokwiintellij.wokwiServer.WokwiCommand
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.generator.nova.PredefinedType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.java_websocket.WebSocket

@Service(Service.Level.PROJECT)
class WokwiSimulationService(val project: Project) : BrowserPipe.Subscriber {
    var connection: WebSocket? = null

    val dataService = project.service<WokwiDataService>()


    fun messageReceived(msg: Map<String, JsonElement>, conn: WebSocket) {

    }

    fun connect(webSocket: WebSocket): Boolean {
        if (this.connection != null) {
            return false
        }

        this.connection = webSocket
        sendStart(webSocket)

        return true
    }

    fun restartAll() {
        if (connection != null) {
            sendStart(connection!!)
        }
    }

    private fun sendStart(webSocket: WebSocket) {
        val image = dataService.retrieveImage()
        if (image == null) {
            WokwiNotifier.notifyBalloon("Failed to retrieve image", project, NotificationType.ERROR)
            return
        }

        val cmd = WokwiCommand.start(image.elf, image.romSegments.toList())

        val json = Json.encodeToString(WokwiCommand.serializer(), cmd)
        webSocket.send(json)
    }

    fun disconnect(webSocket: WebSocket) {
        this.connection = null
    }

    override fun messageReceived(data: String): Boolean {
        println("Received Message: $data")

        val json = Json.decodeFromString<JsonElement>(data)

        when (json.jsonObject["command"]?.jsonPrimitive?.content) {
            "start" -> println("Will start simulator...")
            else -> logger.warn("Command is not supported: $data")
        }

        return true
    }

    companion object {
        val logger = thisLogger()
    }

}