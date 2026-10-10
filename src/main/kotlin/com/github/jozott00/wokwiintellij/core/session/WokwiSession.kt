package com.github.jozott00.wokwiintellij.core.session

import com.github.jozott00.wokwiintellij.core.ports.GdbEvent
import com.github.jozott00.wokwiintellij.core.ports.GdbServer
import com.github.jozott00.wokwiintellij.core.ports.ResourceLoader
import com.github.jozott00.wokwiintellij.core.ports.WokwiTransport
import com.github.jozott00.wokwiintellij.core.protocol.InboundDecodeResult
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.protocol.OutboundMessage
import com.github.jozott00.wokwiintellij.core.protocol.ProtocolCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Base64

/**
 * Owns one Wokwi simulator protocol session.
 *
 * The session is the boundary between IDE-facing code and the Wokwi browser protocol. It subscribes to a
 * [WokwiTransport], waits for Wokwi's readiness message, sends startup payloads, forwards debugger traffic, responds
 * to resource requests, and reports session output through [Listener].
 *
 * This class intentionally avoids IntelliJ, Swing, and JCEF APIs. Platform code supplies those concerns through
 * [WokwiTransport], [ResourceLoader], [GdbServer], and [Listener].
 *
 * @param coroutineScope parent scope used for session-owned background collection jobs.
 * @param transport raw message transport connected to the Wokwi browser wrapper.
 * @param initialConfig startup payload data used when the session is first started.
 * @param resourceLoader callback used to resolve Wokwi `loadResource` requests into bytes.
 * @param gdbServer optional local GDB server connected to Wokwi through this session.
 * @param listener receives observable session events and diagnostics.
 */
class WokwiSession(
    coroutineScope: CoroutineScope,
    private val transport: WokwiTransport,
    initialConfig: WokwiSessionStartConfig,
    private val resourceLoader: ResourceLoader,
    private val gdbServer: GdbServer? = null,
    private val listener: Listener = Listener.NOOP,
) : WokwiTransport.Listener {

    private val sessionJob = SupervisorJob(coroutineScope.coroutineContext[Job])
    private val sessionScope = CoroutineScope(coroutineScope.coroutineContext + sessionJob)
    private var config = initialConfig
    private var browserReady = false
    private var startInvoked = false
    private val stateLock = Any()
    private var disposed = false
    private var startSent = false
    private var debuggerReady = false
    private var simulationStatus: String? = null
    private val resources = Channel<InboundMessage.LoadResource>(Channel.UNLIMITED)

    init {
        transport.subscribe(this)
        // resourceData carries no request id, so replies must preserve request order.
        sessionScope.launch { for (request in resources) loadResource(request) }
        gdbServer?.events
            ?.onEach(::handleGdbEvent)
            ?.launchIn(sessionScope)
    }

    /**
     * Requests simulator startup.
     *
     * The actual startup payload is sent only after Wokwi has also sent its readiness `start` message. Calling this
     * again after readiness resends the current startup config, which is how restarts are represented today.
     */
    fun start() {
        synchronized(stateLock) {
            if (disposed) return
            startInvoked = true
        }
        startInternal()
    }

    /**
     * Replaces the startup payload data used by subsequent [start] calls or readiness-triggered starts.
     */
    fun updateStartConfig(config: WokwiSessionStartConfig) {
        synchronized(stateLock) {
            if (!disposed) this.config = config
        }
    }

    /**
     * Detaches this session from the transport.
     *
     * The transport itself remains owned by the caller.
     */
    fun dispose() {
        synchronized(stateLock) {
            if (disposed) return
            disposed = true
        }
        sessionJob.cancel()
        resources.close()
        transport.removeSubscriber(this)
        listener.onTerminated()
    }

    /**
     * Handles one raw Wokwi-to-IDE protocol payload from [transport].
     *
     * Returns `false` for malformed, unsupported, or unknown messages so transport implementations may log or stop
     * propagation if they support that behavior.
     */
    override fun messageReceived(message: String): Boolean {
        return !synchronized(stateLock) { disposed } && when (val result = ProtocolCodec.decode(message)) {
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
                synchronized(stateLock) { browserReady = true }
                startInternal()
                true
            }
            is InboundMessage.SwitchToBase64 -> {
                listener.onSwitchToBase64Requested()
                true
            }
            is InboundMessage.SimulationRunning -> {
                updateSimulationStatus(message.command)
                true
            }
            is InboundMessage.SimulationPaused -> {
                updateSimulationStatus(message.command)
                true
            }
            is InboundMessage.SimulationStopped -> {
                updateSimulationStatus(message.command)
                true
            }
            is InboundMessage.LoadResource -> {
                resources.trySend(message).isSuccess
            }
            is InboundMessage.UartData -> {
                val bytes = message.toByteArray()
                if (bytes.isNotEmpty()) {
                    listener.onUartData(bytes)
                }
                true
            }
            is InboundMessage.ChipOutput -> {
                listener.onChipOutput(message.chipName, message.message)
                true
            }
            is InboundMessage.GdbResponse -> {
                gdbServer?.sendResponse(message.response)
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
        val startConfig = synchronized(stateLock) {
            if (disposed || !browserReady || !startInvoked) return
            debuggerReady = false
            simulationStatus = null
            startSent = true
            config
        }
        val cmd = ProtocolCodec.encode(
            OutboundMessage.SimulatorStart(
                diagram = startConfig.diagram,
                firmware = Base64.getEncoder().encodeToString(startConfig.firmware),
                firmwareFormat = startConfig.firmwareFormat,
                license = startConfig.license,
                pause = startConfig.waitForDebugger,
                gdbPort = startConfig.gdbPort,
                chips = startConfig.customChips.takeIf { it.isNotEmpty() },
            )
        )
        if (sendIfActive(cmd)) listener.onStarted(startConfig)
    }

    private suspend fun loadResource(message: InboundMessage.LoadResource) {
        try {
            val resource = Base64.getEncoder().encodeToString(resourceLoader.load(message))
            currentCoroutineContext().ensureActive()
            sendIfActive(ProtocolCodec.encode(OutboundMessage.ResourceData(buffer = resource)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!synchronized(stateLock) { disposed }) listener.onResourceError(message, error)
        }
    }

    private fun updateSimulationStatus(status: String) {
        val notifyDebuggerReady = synchronized(stateLock) {
            if (disposed || !startSent || simulationStatus == status) return
            simulationStatus = status
            val initialized = status == InboundMessage.Command.SIM_RUN || status == InboundMessage.Command.SIM_PAUSE
            (initialized && config.waitForDebugger && config.gdbPort != null && !debuggerReady).also {
                if (it) debuggerReady = true
            }
        }
        when (status) {
            InboundMessage.Command.SIM_RUN -> listener.onRunning()
            InboundMessage.Command.SIM_PAUSE -> listener.onPaused()
            InboundMessage.Command.SIM_STOP -> listener.onStopped()
        }
        if (notifyDebuggerReady) listener.onDebuggerReady()
    }

    private fun sendIfActive(message: String): Boolean = synchronized(stateLock) {
        if (disposed) return@synchronized false
        transport.send(message)
        true
    }

    private fun handleGdbEvent(event: GdbEvent) {
        when (event) {
            is GdbEvent.Connected -> sendGdbBreak()
            is GdbEvent.Error -> listener.onGdbError(event)
            is GdbEvent.Message -> sendGdbMessage(event.message)
            is GdbEvent.Break -> sendGdbBreak()
        }
    }

    private fun sendGdbMessage(message: String) {
        sendIfActive(ProtocolCodec.encode(OutboundMessage.Gdb(message = message)))
    }

    private fun sendGdbBreak() {
        sendIfActive(ProtocolCodec.encode(OutboundMessage.GdbBreak()))
    }

    /**
     * Session event sink implemented by IDE-facing adapters.
     */
    interface Listener {
        /** Called after a simulator startup payload has been sent to Wokwi. */
        fun onStarted(config: WokwiSessionStartConfig) {}

        /** Wokwi acknowledged that firmware is running (`sim:run`). */
        fun onRunning() {}

        /** Wokwi acknowledged that the initialized simulator is paused (`sim:pause`). */
        fun onPaused() {}

        /** Wokwi stopped the current simulation (`sim:stop`); the browser can still be restarted. */
        fun onStopped() {}

        /** Called once per debug start after Wokwi acknowledges run/pause with a bound GDB port. */
        fun onDebuggerReady() {}

        /** The host disposed this session. Terminal notification for lifecycle waiters. */
        fun onTerminated() {}

        /** Resource fetching failed; infrastructure errors are separate from protocol decoding errors. */
        fun onResourceError(message: InboundMessage.LoadResource, error: Throwable) {}

        /** Called when Wokwi emits UART bytes. */
        fun onUartData(bytes: ByteArray) {}

        /** Called when Wokwi emits custom chip output. */
        fun onChipOutput(chipName: String, message: String) {}

        /** Called when the local GDB server reports an infrastructure error. */
        fun onGdbError(error: GdbEvent.Error) {}

        /** Called if Wokwi asks for base64 payloads even though the IntelliJ bridge already sends base64. */
        fun onSwitchToBase64Requested() {}

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

    /** Local GDB server port to expose to Wokwi when debugger support is active. */
    val gdbPort: Int? = null,

    /** Custom chip definitions to load before the simulation starts. */
    val customChips: List<com.github.jozott00.wokwiintellij.core.model.CustomChip> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WokwiSessionStartConfig) return false
        return license == other.license
            && diagram == other.diagram
            && firmware.contentEquals(other.firmware)
            && firmwareFormat == other.firmwareFormat
            && waitForDebugger == other.waitForDebugger
            && gdbPort == other.gdbPort
            && customChips == other.customChips
    }

    override fun hashCode(): Int {
        var result = license.hashCode()
        result = 31 * result + diagram.hashCode()
        result = 31 * result + firmware.contentHashCode()
        result = 31 * result + firmwareFormat.hashCode()
        result = 31 * result + waitForDebugger.hashCode()
        result = 31 * result + gdbPort.hashCode()
        result = 31 * result + customChips.hashCode()
        return result
    }
}
