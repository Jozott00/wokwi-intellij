package com.github.jozott00.wokwiintellij.core.session

import com.github.jozott00.wokwiintellij.core.ports.WokwiTransport
import com.github.jozott00.wokwiintellij.core.protocol.InboundDecodeResult
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.protocol.OutboundMessage
import com.github.jozott00.wokwiintellij.core.protocol.ProtocolCodec
import java.util.Base64

/**
 * Owns one Wokwi simulator protocol session.
 *
 * The session is the boundary between IDE-facing code and the Wokwi browser protocol. It subscribes to a
 * [WokwiTransport], waits for Wokwi's readiness message, sends startup payloads, forwards debugger traffic, responds
 * to resource requests, and reports session output through [Listener].
 *
 * This class intentionally avoids IntelliJ, Swing, and JCEF APIs. Platform code supplies those concerns through
 * [WokwiTransport], [ResourceLoader], and [Listener].
 *
 * @param transport raw message transport connected to the Wokwi browser wrapper.
 * @param initialConfig startup payload data used when the session is first started.
 * @param resourceLoader callback used to resolve Wokwi `loadResource` requests into bytes.
 * @param listener receives observable session events and diagnostics.
 */
class WokwiSession(
    private val transport: WokwiTransport,
    initialConfig: WokwiSessionStartConfig,
    private val resourceLoader: ResourceLoader,
    private val listener: Listener = Listener.NOOP,
) : WokwiTransport.Listener {

    private var config = initialConfig
    private var browserReady = false
    private var startInvoked = false
    private var simulationRunning = false

    init {
        transport.subscribe(this)
    }

    /**
     * Requests simulator startup.
     *
     * The actual startup payload is sent only after Wokwi has also sent its readiness `start` message. Calling this
     * again after readiness resends the current startup config, which is how restarts are represented today.
     */
    fun start() {
        startInvoked = true
        startInternal()
    }

    /**
     * Replaces the startup payload data used by subsequent [start] calls or readiness-triggered starts.
     */
    fun updateStartConfig(config: WokwiSessionStartConfig) {
        this.config = config
    }

    /**
     * Sends one remote GDB protocol command to Wokwi.
     */
    fun sendGdbMessage(message: String) {
        transport.send(ProtocolCodec.encode(OutboundMessage.Gdb(message = message)))
    }

    /**
     * Requests a debugger break/pause in Wokwi.
     */
    fun sendGdbBreak() {
        transport.send(ProtocolCodec.encode(OutboundMessage.GdbBreak()))
    }

    /**
     * Detaches this session from the transport.
     *
     * The transport itself remains owned by the caller.
     */
    fun dispose() {
        transport.removeSubscriber(this)
    }

    /**
     * Handles one raw Wokwi-to-IDE protocol payload from [transport].
     *
     * Returns `false` for malformed, unsupported, or unknown messages so transport implementations may log or stop
     * propagation if they support that behavior.
     */
    override fun messageReceived(message: String): Boolean {
        return when (val result = ProtocolCodec.decode(message)) {
            InboundDecodeResult.Empty -> true
            is InboundDecodeResult.Malformed -> {
                listener.onMalformedMessage(result)
                false
            }
            is InboundDecodeResult.Decoded -> handleIncomingMessage(result.message)
        }
    }

    private fun handleIncomingMessage(message: InboundMessage): Boolean {
        return when (message) {
            is InboundMessage.Ready -> {
                browserReady = true
                startInternal()
                true
            }
            is InboundMessage.LoadResource -> {
                loadResource(message)
                true
            }
            is InboundMessage.UartData -> {
                val bytes = message.toByteArray()
                if (bytes.isNotEmpty()) {
                    listener.onUartData(bytes)
                }
                true
            }
            is InboundMessage.GdbResponse -> {
                listener.onGdbResponse(message.response)
                true
            }
            is InboundMessage.WifiConnect, is InboundMessage.WifiFrame -> {
                listener.onUnsupportedMessage(message)
                false
            }
            is InboundMessage.Unknown -> {
                listener.onUnknownMessage(message)
                false
            }
        }
    }

    private fun startInternal() {
        simulationRunning = false

        if (!browserReady || !startInvoked) return

        val cmd = ProtocolCodec.encode(
            OutboundMessage.SimulatorStart(
                diagram = config.diagram,
                firmware = Base64.getEncoder().encodeToString(config.firmware),
                firmwareFormat = config.firmwareFormat,
                license = config.license,
                pause = config.waitForDebugger,
            )
        )
        transport.send(cmd)
        listener.onStarted(config)
    }

    private fun loadResource(message: InboundMessage.LoadResource) {
        val resource = Base64.getEncoder().encodeToString(resourceLoader.load(message))
        val cmd = ProtocolCodec.encode(OutboundMessage.ResourceData(buffer = resource))
        transport.send(cmd)

        checkSimulationStartedRunning()
    }

    private fun checkSimulationStartedRunning() {
        if (!simulationRunning) {
            simulationRunning = true
            listener.onRunning()
        }
    }

    /**
     * Resolves resources requested by Wokwi while starting or running the simulation.
     */
    fun interface ResourceLoader {
        /**
         * Returns raw bytes for [message]. The session handles transport encoding before replying to Wokwi.
         */
        fun load(message: InboundMessage.LoadResource): ByteArray
    }

    /**
     * Session event sink implemented by IDE-facing adapters.
     */
    interface Listener {
        /** Called after a simulator startup payload has been sent to Wokwi. */
        fun onStarted(config: WokwiSessionStartConfig) {}

        /** Called once when the session first observes resource loading activity for a start cycle. */
        fun onRunning() {}

        /** Called when Wokwi emits UART bytes. */
        fun onUartData(bytes: ByteArray) {}

        /** Called when Wokwi replies to a forwarded GDB command. */
        fun onGdbResponse(response: String) {}

        /** Called when inbound JSON cannot be decoded into a valid protocol message. */
        fun onMalformedMessage(message: InboundDecodeResult.Malformed) {}

        /** Called when Wokwi sends a syntactically valid command this plugin does not model yet. */
        fun onUnknownMessage(message: InboundMessage.Unknown) {}

        /** Called for modeled commands that this session does not implement yet. */
        fun onUnsupportedMessage(message: InboundMessage) {}

        companion object {
            val NOOP = object : Listener {}
        }
    }
}

/**
 * Pure startup data for a Wokwi simulator run.
 *
 * IDE-specific file handles and project services should be resolved before constructing this model.
 */
data class WokwiSessionStartConfig(
    /** Wokwi license string passed through to the simulator. */
    val license: String,

    /** Raw `diagram.json` content. */
    val diagram: String,

    /** Firmware bytes. The session base64-encodes them for the current bridge. */
    val firmware: ByteArray,

    /** Wokwi firmware format name, for example `bin`, `hex`, or `uf2`. */
    val firmwareFormat: String,

    /** Starts Wokwi paused so a debugger can attach before execution. */
    val waitForDebugger: Boolean,
)
