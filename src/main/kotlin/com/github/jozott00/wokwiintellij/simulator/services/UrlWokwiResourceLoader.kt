package com.github.jozott00.wokwiintellij.simulator.services

import com.github.jozott00.wokwiintellij.core.ports.ResourceLoader
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UrlWokwiResourceLoader : ResourceLoader {
    override suspend fun load(message: InboundMessage.LoadResource): ByteArray = withContext(Dispatchers.IO) {
        // TODO: Prefer bundled/offline resources when available.
        val connection = java.net.URI(message.url).toURL().openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        connection.getInputStream().use { it.readBytes() }
    }
}
