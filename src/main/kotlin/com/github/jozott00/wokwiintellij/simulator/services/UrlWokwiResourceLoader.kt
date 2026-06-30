package com.github.jozott00.wokwiintellij.simulator.services

import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.session.WokwiSession

class UrlWokwiResourceLoader : WokwiSession.ResourceLoader {
    override fun load(message: InboundMessage.LoadResource): ByteArray {
        // TODO: Prefer bundled/offline resources when available.
        return java.net.URI(message.url).toURL().readBytes()
    }
}
