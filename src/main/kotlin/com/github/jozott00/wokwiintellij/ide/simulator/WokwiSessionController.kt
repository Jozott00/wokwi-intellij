package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.ports.GdbEvent
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.ide.execution.processHandler.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.ide.execution.processHandler.WokwiRunProcessHandler
import com.github.jozott00.wokwiintellij.ide.services.IntelliJUserNotifier
import com.github.jozott00.wokwiintellij.ide.services.SimulationConfigLoader
import com.github.jozott00.wokwiintellij.ide.services.WokwiComponentService
import com.github.jozott00.wokwiintellij.services.UserNotifier
import com.github.jozott00.wokwiintellij.simulator.services.UrlWokwiResourceLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

/** Serializes project simulation transitions; stop invalidates and cancels pending startup immediately. */
@Service(Service.Level.PROJECT)
class WokwiSessionController internal constructor(
    cs: CoroutineScope,
    private val runtimeFactory: SimulationRuntimeFactory,
    private val gdbServerManager: GdbServerManager,
    private val processHandlerFactory: () -> WokwiProcessHandler,
    private val userNotifier: UserNotifier,
) : Disposable {
    constructor(project: Project, cs: CoroutineScope) : this(
        cs = cs,
        runtimeFactory = WokwiSimulationRuntimeFactory(
            cs, project.service<SimulationConfigLoader>(), UrlWokwiResourceLoader(),
        ),
        gdbServerManager = WokwiGdbServerManager(project, cs),
        processHandlerFactory = { WokwiRunProcessHandler(project) },
        userNotifier = IntelliJUserNotifier,
    ) {
        eventDispatcher.subscribePersistent(WokwiSessionDiagnosticsListener(LOG))
        lifecycleDispatcher.subscribePersistent(project.service<WokwiComponentService>().simulatorToolWindowPresenter)
    }

    private val controllerJob = SupervisorJob(cs.coroutineContext[Job])
    private val controllerScope = CoroutineScope(cs.coroutineContext + controllerJob)
    // Reserve requests synchronously; serialize suspending work separately without holding this lock.
    private val stateLock = Any()
    private val transitionMutex = Mutex()
    private val eventDispatcher = WokwiSessionEventDispatcher()
    private val lifecycleDispatcher = WokwiSimulationLifecycleDispatcher()
    private var state: State = State.Idle

    private data class ActiveSimulation(val runtime: SimulationRuntime, val handler: WokwiProcessHandler?, val token: Any)
    private class StartRequest(
        val listener: WokwiSession.Listener?,
        val handler: WokwiProcessHandler?,
        val byDebugger: Boolean,
    ) {
        lateinit var job: Deferred<Boolean>
    }

    /** Runtime ownership and startup eligibility move together, under stateLock. */
    private sealed class State {
        open val owned: ActiveSimulation? get() = null
        open val request: StartRequest? get() = null

        data object Idle : State()
        data class Starting(override val request: StartRequest, override val owned: ActiveSimulation?) : State()
        // A new request may be reserved before the preceding Stop has released its runtime and GDB server.
        data class StartingAfterStop(override val request: StartRequest, override val owned: ActiveSimulation?) : State()
        data class Active(override val request: StartRequest, override val owned: ActiveSimulation) : State()
        // A failed replacement can leave the previous runtime usable.
        data class Failed(override val request: StartRequest, override val owned: ActiveSimulation?) : State()
        data class Stopping(override val owned: ActiveSimulation?) : State()
        data class Disposing(override val owned: ActiveSimulation?) : State()
        data object Disposed : State()
    }

    private fun pendingRequest(): StartRequest? = when (val current = state) {
        is State.Starting -> current.request
        is State.StartingAfterStop -> current.request
        else -> null
    }

    private fun usableSimulation(): ActiveSimulation? = when (val current = state) {
        is State.Starting -> current.owned
        is State.Active -> current.owned
        is State.Failed -> current.owned
        else -> null
    }

    private fun isDisposingOrDisposed(): Boolean = state is State.Disposing || state === State.Disposed

    /** Returns the console handler before asynchronous configuration loading begins. */
    fun startSimulator(byDebugger: Boolean = false): WokwiProcessHandler {
        val request = synchronized(stateLock) {
            val handler = if (byDebugger) null else
                (pendingRequest()?.handler ?: state.owned?.handler)?.takeUnless { it.isProcessTerminated || it.isProcessTerminating }
            enqueueStart(null, handler ?: processHandlerFactory(), byDebugger)
        }
        request.job.start()
        return requireNotNull(request.handler)
    }

    /** Starts or reloads a session. A superseded or stopped request returns false. */
    suspend fun startSimulatorAsync(listener: WokwiSession.Listener? = null, byDebugger: Boolean = false): Boolean {
        val request = synchronized(stateLock) { enqueueStart(listener, null, byDebugger) }
        request.job.start()
        return awaitRequest(request)
    }

    /** Waits for the simulator's run/pause acknowledgement, bounded by a startup timeout. */
    suspend fun startDebuggerAndAwaitReady(timeoutMillis: Long = 30_000): Boolean {
        val ready = CompletableDeferred<Boolean>()
        var readinessFailed = false
        fun failReadiness() = synchronized(stateLock) {
            readinessFailed = true
            ready.complete(false)
        }
        val listener = object : WokwiSession.Listener {
            override fun onDebuggerReady() { ready.complete(true) }
            override fun onTerminated() { failReadiness() }
            override fun onStopped() { failReadiness() }
            override fun onGdbError(error: GdbEvent.Error) {
                failReadiness()
            }
            override fun onResourceError(message: InboundMessage.LoadResource, error: Throwable) {
                failReadiness()
            }
        }
        val request = synchronized(stateLock) { enqueueStart(listener, null, true) }
        request.job.start()
        try {
            val acknowledged = withTimeoutOrNull(timeoutMillis.milliseconds) {
                awaitRequest(request) && ready.await()
            } ?: run {
                userNotifier.error("Wokwi debugger startup timed out", "The simulator did not acknowledge startup within the timeout.")
                false
            }
            // A one-shot acknowledgement can already be obsolete when its waiter resumes.
            val result = acknowledged && synchronized(stateLock) {
                !readinessFailed && state.request === request && usableSimulation() != null
            }
            if (!result) requestStop(request)
            return result
        } catch (error: CancellationException) {
            requestStop(request)
            throw error
        } finally {
            eventDispatcher.unsubscribe(listener)
        }
    }

    fun stopSimulator(): Job = requestStop()

    private fun requestStop(expectedRequest: StartRequest? = null): Job {
        synchronized(stateLock) {
            if (isDisposingOrDisposed() || (expectedRequest != null && state.request !== expectedRequest)) {
                return Job().apply { complete() }
            }
            val pending = pendingRequest()
            val handlers = listOfNotNull(state.owned?.handler, pending?.handler).distinct()
            state = State.Stopping(state.owned)
            pending?.job?.cancel()
            handlers.forEach { it.onShutdown(SimExitCode.OK) }
        }
        return controllerScope.launch { transitionMutex.withLock { cleanupRequestedStop() } }
    }

    fun getRunningGDBPort(): Int? = synchronized(stateLock) { gdbServerManager.runningPort() }
    fun getWatchPaths(): List<Path>? = synchronized(stateLock) {
        usableSimulation()?.runtime?.simulationConfig?.firmware?.watchPaths
    }
    fun isSimulatorRunning(): Boolean = synchronized(stateLock) { usableSimulation() != null }

    fun firmwareUpdated(): Job {
        val request = synchronized(stateLock) {
            if (usableSimulation() == null) return Job().apply { complete() }
            enqueueStart(null, null, false)
        }
        userNotifier.info("New firmware detected", "Restarting Wokwi simulator...")
        request.job.start()
        return request.job
    }

    override fun dispose() {
        synchronized(stateLock) {
            if (isDisposingOrDisposed()) return
            val pending = pendingRequest()
            state = State.Disposing(state.owned)
            pending?.job?.cancel()
        }
        controllerJob.cancel()
        cleanupRequestedStop()
    }

    /** Called with stateLock held so selecting a handler and reserving its request are atomic. */
    private fun enqueueStart(listener: WokwiSession.Listener?, handler: WokwiProcessHandler?, byDebugger: Boolean): StartRequest {
        val previous = pendingRequest()
        val request = StartRequest(listener, handler, byDebugger)
        request.job = controllerScope.async(start = CoroutineStart.LAZY) {
            val started = try {
                transitionMutex.withLock {
                    ensureCurrent(request)
                    cleanupRequestedStop()
                    prepareStart(request)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LOG.warn("Failed to start Wokwi simulator", error)
                userNotifier.error("Failed to start Wokwi simulator", error.message ?: "Unexpected startup error.")
                false
            }
            synchronized(stateLock) {
                if (state.request === request) {
                    state = if (started) State.Active(request, requireNotNull(state.owned))
                    else State.Failed(request, state.owned)
                }
            }
            started
        }
        state = when (val current = state) {
            is State.Stopping, is State.StartingAfterStop -> State.StartingAfterStop(request, current.owned)
            is State.Disposing, State.Disposed -> current
            else -> State.Starting(request, current.owned)
        }
        request.job.invokeOnCompletion { error ->
            val unusedHandler = synchronized(stateLock) {
                if (pendingRequest() === request) {
                    // Cancellation can finish a request before it reaches the normal completion transition.
                    state = when (val current = state) {
                        is State.StartingAfterStop -> State.Stopping(current.owned)
                        else -> State.Failed(request, current.owned)
                    }
                }
                request.handler?.takeUnless { state.owned?.handler === it || pendingRequest()?.handler === it }
            }
            unusedHandler?.onShutdown(if (error is CancellationException) SimExitCode.OK else SimExitCode.CONFIG_ERROR)
        }
        previous?.job?.cancel()
        if (isDisposingOrDisposed()) request.job.cancel()
        return request
    }

    private suspend fun awaitRequest(request: StartRequest): Boolean = try {
        request.job.await()
    } catch (_: CancellationException) {
        request.job.cancel()
        currentCoroutineContext().ensureActive()
        false
    }

    private suspend fun ensureCurrent(request: StartRequest) {
        currentCoroutineContext().ensureActive()
        synchronized(stateLock) { checkCurrent(request) }
    }

    private fun checkCurrent(request: StartRequest) {
        if (state.request !== request) throw CancellationException("Simulation startup was superseded or stopped")
    }

    private suspend fun prepareStart(request: StartRequest): Boolean {
        val previous = synchronized(stateLock) { state.owned }
        if (previous != null && !request.byDebugger) {
            // The debugger before-run task already started the session. Attaching its Run console must not restart it.
            val attachConsoleOnly = previous.handler == null && request.handler != null &&
                previous.runtime.simulationConfig.waitForDebugger
            val firmware = if (attachConsoleOnly) null else runtimeFactory.loadFirmware(previous.runtime.simulationConfig) ?: return false
            ensureCurrent(request)
            synchronized(stateLock) {
                checkCurrent(request)
                if (firmware != null) {
                    val config = previous.runtime.simulationConfig.copy(firmware = firmware)
                    previous.runtime.simulationConfig = config
                    previous.runtime.session.updateStartConfig(runtimeFactory.createStartConfig(config, gdbServerManager.runningPort()))
                }
                bindHandler(previous.runtime, request, previous.handler, previous.token)
                if (!attachConsoleOnly) previous.runtime.session.start()
            }
            return true
        }

        val loaded = runtimeFactory.loadConfig(request.byDebugger) ?: return false
        if (!runtimeFactory.ensureBrowserSupported()) return false
        ensureCurrent(request)
        synchronized(stateLock) {
            checkCurrent(request)
            disposeActive(request.handler)
        }
        val gdbServer = synchronized(stateLock) {
            checkCurrent(request)
            gdbServerManager.configure(request.byDebugger, loaded.gdbServerPort)
        }
        if (request.byDebugger && gdbServer == null) {
            lifecycleDispatcher.simulationStopped()
            return false
        }
        val boundConfig = loaded.copy(gdbServerPort = gdbServerManager.runningPort())
        val token = Any()
        val sessionListener = eventDispatcher.asSessionListener { event ->
            synchronized(stateLock) { if (state.owned?.token === token) event() }
        }
        var candidate: SimulationRuntime? = null
        var committed = false
        try {
            val runtime = runtimeFactory.createRuntime(boundConfig, gdbServer, sessionListener)
            candidate = runtime
            ensureCurrent(request)
            synchronized(stateLock) {
                checkCurrent(request)
                bindHandler(runtime, request, null, token)
                lifecycleDispatcher.simulationViewReady(runtime.component)
                runtime.session.start()
                committed = true // ownership transferred to the controller state
            }
            return true
        } finally {
            if (!committed) synchronized(stateLock) {
                if (state.owned?.runtime === candidate) disposeActive() else candidate?.dispose()
                gdbServerManager.disposeServer()
                lifecycleDispatcher.simulationStopped()
            }
        }
    }

    private fun bindHandler(runtime: SimulationRuntime, request: StartRequest, previousHandler: WokwiProcessHandler?, token: Any) {
        val handler = request.handler ?: previousHandler
        if (previousHandler != null && previousHandler !== handler) {
            eventDispatcher.unsubscribe(previousHandler)
            previousHandler.onShutdown(SimExitCode.OK)
        }
        request.listener?.let(eventDispatcher::subscribe)
        handler?.let(eventDispatcher::subscribe)
        state = State.Starting(request, ActiveSimulation(runtime, handler, token))
    }

    private fun disposeActive(preservedHandler: WokwiProcessHandler? = null) {
        val previous = state.owned
        previous?.runtime?.dispose()
        state = when (val current = state) {
            is State.Starting -> current.copy(owned = null)
            is State.StartingAfterStop -> current.copy(owned = null)
            is State.Active -> State.Idle
            is State.Failed -> current.copy(owned = null)
            is State.Stopping -> current.copy(owned = null)
            is State.Disposing -> current.copy(owned = null)
            State.Idle, State.Disposed -> current
        }
        previous?.handler?.takeUnless { it === preservedHandler }?.onShutdown(SimExitCode.OK)
        eventDispatcher.clearSessionSubscribers()
    }

    private fun cleanupRequestedStop() = synchronized(stateLock) {
        if (state !is State.Stopping && state !is State.StartingAfterStop && state !is State.Disposing) return@synchronized
        disposeActive()
        gdbServerManager.disposeServer()
        lifecycleDispatcher.simulationStopped()
        state = when (val current = state) {
            is State.StartingAfterStop -> State.Starting(current.request, null)
            is State.Disposing -> State.Disposed
            is State.Stopping -> State.Idle
            else -> current
        }
    }

    companion object {
        private val LOG = logger<WokwiSessionController>()
    }
}
