package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.model.SimulationConfig
import com.github.jozott00.wokwiintellij.core.protocol.InboundDecodeResult
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.core.session.WokwiSessionStartConfig
import com.github.jozott00.wokwiintellij.simulator.SimExitCode
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ContainerUtil

/**
 * Adapts pure session callbacks to current IntelliJ simulator listeners.
 *
 * This bridge is the compatibility boundary between [WokwiSession.Listener] and the existing run/process UI listener
 * API. It also converts UART byte chunks into IntelliJ console text with ANSI escape handling.
 *
 * @property currentConfig returns the latest simulation config for listener events that expose IDE-level config data.
 * @property log logger used for protocol and debugger errors surfaced by the core session.
 */
class WokwiSimulatorEventBridge(
    private val currentConfig: () -> SimulationConfig?,
    private val log: Logger,
) {
    private val ansiEscapeDecoder = AnsiEscapeDecoder()
    private val simulatorListeners: MutableList<WokwiSimulatorListener> =
        ContainerUtil.createLockFreeCopyOnWriteList()

    /**
     * Registers a listener for simulator lifecycle and console events.
     */
    fun addListener(listener: WokwiSimulatorListener) {
        simulatorListeners.add(listener)
    }

    /**
     * Removes all registered listeners, usually when the active runtime is replaced or stopped.
     */
    fun clearListeners() {
        simulatorListeners.clear()
    }

    /**
     * Notifies registered listeners that the simulator has shut down.
     */
    fun notifyShutdown(exitCode: SimExitCode) {
        notifySimulatorListeners { it.onShutdown(exitCode) }
    }

    /**
     * Creates the core session listener used by each active [WokwiSession].
     */
    fun createSessionListener(): WokwiSession.Listener {
        return object : WokwiSession.Listener {
            override fun onStarted(config: WokwiSessionStartConfig) {
                log.info("(Re)starting simulation...")
                currentConfig()?.let { simulationConfig ->
                    notifySimulatorListeners { listener -> listener.onStarted(simulationConfig) }
                }
            }

            override fun onRunning() {
                notifySimulatorListeners { it.onRunning() }
            }

            override fun onUartData(bytes: ByteArray) {
                if (bytes.isEmpty()) return

                val text = String(bytes, Charsets.UTF_8)
                ansiEscapeDecoder.escapeText(text, ProcessOutputTypes.STDOUT) { decodedText, contentType ->
                    notifySimulatorListeners { it.onTextAvailable(decodedText, contentType) }
                }
            }

            override fun onGdbError(error: Throwable) {
                log.error("GDB server error", error)
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
    }

    private fun notifySimulatorListeners(event: (WokwiSimulatorListener) -> Unit) {
        for (listener in simulatorListeners) {
            event(listener)
        }
    }
}
