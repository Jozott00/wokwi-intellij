package com.github.jozott00.wokwiintellij.wokwiServer

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.services.WokwiSimulationService
import com.intellij.openapi.components.service
import com.intellij.openapi.components.services
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress


// WokwiServer class
class WokwiServer(port: Int, project: Project) : WebSocketServer(InetSocketAddress(port)) {

    val service = project.service<WokwiSimulationService>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        service.connect(conn)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        service.disconnect(conn)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val data = Json.parseToJsonElement(message)
        require(data is JsonObject) { "Failed to parse received message $message" }
        this.service.messageReceived(data, conn)
    }

    override fun onStart() {
        
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        ex.printStackTrace()
    }
}
