package com.github.jozott00.wokwiintellij.simulator.gdb

import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.intellij.openapi.diagnostic.logger

class WokwiSessionGdbBridge(
    private val session: WokwiSession,
    private val server: GDBServerCommunicator,
) {

    suspend fun connect() {
        server.getMessageFlow().collect { event ->
            when (event) {
                is GDBServerEvent.Connected -> {}
                is GDBServerEvent.Error -> LOG.error("Error: ${event.error}")
                is GDBServerEvent.Message -> session.sendGdbMessage(event.message)
                is GDBServerEvent.Break -> session.sendGdbBreak()
            }
        }
    }

    fun sendResponse(response: String) {
        server.sendResponse(response)
    }

    companion object {
        private val LOG = logger<WokwiSessionGdbBridge>()
    }
}
