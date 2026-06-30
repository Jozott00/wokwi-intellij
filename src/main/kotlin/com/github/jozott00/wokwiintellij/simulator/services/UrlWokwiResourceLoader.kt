package com.github.jozott00.wokwiintellij.simulator.services

import com.github.jozott00.wokwiintellij.core.ports.ResourceLoader
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage

class UrlWokwiResourceLoader : ResourceLoader {
    override fun load(message: InboundMessage.LoadResource): ByteArray {
        // TODO: Prefer bundled/offline resources when available.
        return java.net.URI(message.url).toURL().readBytes()
    }
}
