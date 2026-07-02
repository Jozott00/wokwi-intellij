package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.ports.GdbEvent
import com.github.jozott00.wokwiintellij.core.protocol.InboundDecodeResult
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.core.session.WokwiSessionStartConfig
import com.github.jozott00.wokwiintellij.ide.services.IntelliJUserNotifier
import com.github.jozott00.wokwiintellij.services.UserNotifier
import com.intellij.openapi.diagnostic.Logger

/**
 * IDE diagnostics subscriber for session-level warnings and infrastructure errors.
 */
class WokwiSessionDiagnosticsListener(
    private val log: Logger,
    private val userNotifier: UserNotifier = IntelliJUserNotifier,
) : WokwiSession.Listener {

    override fun onStarted(config: WokwiSessionStartConfig) {
        log.info("(Re)starting simulation...")
    }

    override fun onGdbError(error: GdbEvent.Error) {
        error.cause?.let { cause ->
            log.error(error.message, cause)
        } ?: log.error(error.message)
        userNotifier.error(
            error.title,
            error.message,
        )
    }

    override fun onSwitchToBase64Requested() {
        log.info("Wokwi requested base64 payloads; IntelliJ already sends firmware and resources as base64.")
    }

    override fun onMalformedMessage(message: InboundDecodeResult.Malformed) {
        log.error("Malformed Wokwi message: ${message.reason}\n${message.raw}", Throwable())
    }

    override fun onUnknownMessage(message: InboundMessage.Unknown) {
        log.warn("Unknown command: ${message.command}")
        log.debug("Unknown command data: ${message.raw}")
    }

    override fun onUnsupportedMessage(message: InboundMessage) {
        log.warn("Unsupported Wokwi command: ${message.command}")
    }
}
