package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.github.jozott00.wokwiintellij.wokwiServer.WokwiCommand
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.generator.nova.PredefinedType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.java_websocket.WebSocket

@Service(Service.Level.PROJECT)
class WokwiSimulationService(val project: Project) {
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

}