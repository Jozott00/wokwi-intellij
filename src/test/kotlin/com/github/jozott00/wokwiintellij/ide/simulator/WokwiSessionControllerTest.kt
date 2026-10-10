package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.model.FirmwareFormat
import com.github.jozott00.wokwiintellij.core.model.FirmwareImage
import com.github.jozott00.wokwiintellij.core.model.SimulationConfig
import com.github.jozott00.wokwiintellij.core.ports.GdbEvent
import com.github.jozott00.wokwiintellij.core.ports.GdbServer
import com.github.jozott00.wokwiintellij.core.ports.ResourceLoader
import com.github.jozott00.wokwiintellij.core.ports.WokwiTransport
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.ide.execution.processHandler.WokwiProcessHandler
import com.github.jozott00.wokwiintellij.ide.services.LoadedSimulationConfig
import com.github.jozott00.wokwiintellij.services.UserNotificationAction
import com.github.jozott00.wokwiintellij.services.UserNotificationType
import com.github.jozott00.wokwiintellij.services.UserNotifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.OutputStream
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/** Tests real session behavior with controlled startup boundaries, without an IDE or browser. */
class WokwiSessionControllerTest {
    @Test
    fun `stop rejects configuration that finishes after cancellation`() = runBlocking {
        Fixture().use { fixture ->
            val release = CompletableDeferred<Unit>()
            fixture.factory.load = { debug ->
                withContext(NonCancellable) { release.await() }
                fixture.factory.config(debug)
            }
            val handler = fixture.controller.startSimulator()
            val stopped = fixture.controller.stopSimulator()
            assertTrue(handler.isProcessTerminated)
            release.complete(Unit)
            withTimeout(2_000) { stopped.join() }
            assertFalse(fixture.controller.isSimulatorRunning())
            assertTrue(fixture.factory.created.isEmpty())
        }
    }

    @Test
    fun `new startup supersedes pending startup and keeps its handler alive`() = runBlocking {
        Fixture().use { fixture ->
            val release = CompletableDeferred<Unit>()
            var calls = 0
            fixture.factory.load = { debug ->
                if (++calls == 1) withContext(NonCancellable) { release.await() }
                fixture.factory.config(debug)
            }
            val first = fixture.controller.startSimulator()
            val second = fixture.controller.startSimulator(true)
            release.complete(Unit)
            assertTrue(first.isProcessTerminated)
            assertFalse(second.isProcessTerminated)
            assertTrue(fixture.controller.isSimulatorRunning())
            assertEquals(1, fixture.factory.created.size)
            assertTrue(fixture.factory.created.single().simulationConfig.waitForDebugger)
        }
    }

    @Test
    fun `stop then start serializes cleanup before creating the new runtime`() = runBlocking {
        Fixture().use { fixture ->
            val release = CompletableDeferred<Unit>()
            var calls = 0
            fixture.factory.load = { debug ->
                if (++calls == 1) withContext(NonCancellable) { release.await() }
                fixture.factory.config(debug)
            }
            val first = fixture.controller.startSimulator()
            val stopped = fixture.controller.stopSimulator()
            val second = fixture.controller.startSimulator()
            release.complete(Unit)
            withTimeout(2_000) { stopped.join() }
            assertTrue(first.isProcessTerminated)
            assertFalse(second.isProcessTerminated)
            assertTrue(fixture.controller.isSimulatorRunning())
            assertEquals(1, fixture.factory.created.size)
        }
    }

    @Test
    fun `stop cancels a queued firmware watch restart instead of allowing it to create a new runtime`() {
        val dispatcher = QueuedDispatcher()
        Fixture(dispatcher).use { fixture ->
            val handler = fixture.controller.startSimulator()
            dispatcher.drain()
            assertTrue(fixture.controller.isSimulatorRunning())
            val reload = fixture.controller.firmwareUpdated()
            fixture.controller.stopSimulator()
            dispatcher.drain()
            assertTrue(reload.isCancelled)
            assertTrue(handler.isProcessTerminated)
            assertFalse(fixture.controller.isSimulatorRunning())
            assertEquals(1, fixture.factory.created.size)
            assertEquals(0, fixture.factory.firmwareLoads)
            fixture.controller.firmwareUpdated()
            dispatcher.drain()
            assertFalse(fixture.controller.isSimulatorRunning())
        }
        dispatcher.drain()
    }

    @Test
    fun `stop during browser construction disposes the unpublished runtime and gdb server`() = runBlocking {
        Fixture().use { fixture ->
            val release = CompletableDeferred<Unit>()
            fixture.factory.beforeCreate = { withContext(NonCancellable) { release.await() } }
            fixture.controller.startSimulator(true)
            assertNotNull(fixture.gdb.runningPort())
            val stopped = fixture.controller.stopSimulator()
            release.complete(Unit)
            withTimeout(2_000) { stopped.join() }
            assertEquals(1, fixture.factory.created.single().disposeCount)
            assertNull(fixture.gdb.runningPort())
            assertFalse(fixture.controller.isSimulatorRunning())
        }
    }

    @Test
    fun `new starts reserved before stop cleanup preserve cleanup and share only the new console`() {
        val dispatcher = QueuedDispatcher()
        Fixture(dispatcher).use { fixture ->
            val oldHandler = fixture.controller.startSimulator()
            dispatcher.drain()
            val previous = fixture.factory.created.single()
            fixture.controller.stopSimulator()
            val first = fixture.controller.startSimulator()
            val second = fixture.controller.startSimulator()
            assertTrue(oldHandler.isProcessTerminated)
            assertSame(first, second)
            assertFalse(second.isProcessTerminated)
            assertFalse(fixture.controller.isSimulatorRunning())
            dispatcher.drain()
            assertEquals(1, previous.disposeCount)
            assertEquals(2, fixture.factory.created.size)
            assertFalse(second.isProcessTerminated)
            assertTrue(fixture.controller.isSimulatorRunning())
        }
    }

    @Test
    fun `debug runtime replacement terminates the old console while preserving the new console`() {
        Fixture().use { fixture ->
            val old = fixture.controller.startSimulator()
            val previous = fixture.factory.created.single()
            val current = fixture.controller.startSimulator(true)
            assertTrue(old.isProcessTerminated)
            assertFalse(current.isProcessTerminated)
            assertEquals(1, previous.disposeCount)
            assertEquals(2, fixture.factory.created.size)

            val reused = fixture.controller.startSimulator()
            assertSame(current, reused)
            assertFalse(reused.isProcessTerminated)
            assertEquals(2, fixture.factory.created.size)
            assertEquals(1, fixture.factory.firmwareLoads)
            assertEquals(2, fixture.factory.created.last().transport.sent.size)
        }
    }

    @Test
    fun `late callbacks from retired runtime cannot reach the new console or debugger waiter`() = runBlocking {
        Fixture().use { fixture ->
            fixture.controller.startSimulator()
            val retired = fixture.factory.listeners.single()
            val ready = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            retired.onDebuggerReady()
            retired.onTerminated()
            assertFalse(ready.isCompleted)
            fixture.factory.created.last().transport.receive("sim:pause")
            assertTrue(withTimeout(2_000) { ready.await() })
            val current = fixture.controller.startSimulator() as RecordingHandler
            retired.onUartData(byteArrayOf(1))
            assertTrue(current.output.isEmpty())
            fixture.factory.listeners.last().onUartData(byteArrayOf(2))
            assertContentEquals(byteArrayOf(2), current.output.single())
        }
    }

    @Test
    fun `failed configuration terminates its pending console and debugger fails without waiting`() = runBlocking {
        Fixture().use { fixture ->
            fixture.factory.load = { null }
            val handler = fixture.controller.startSimulator() as RecordingHandler
            assertTrue(handler.isProcessTerminated)
            assertEquals(SimExitCode.CONFIG_ERROR.int, handler.exitCode)
            assertFalse(withTimeout(2_000) { fixture.controller.startDebuggerAndAwaitReady() })
            assertTrue(fixture.factory.created.isEmpty())
        }
    }

    @Test
    fun `failed replacement leaves the old runtime and console usable for another reload`() {
        Fixture().use { fixture ->
            val oldHandler = fixture.controller.startSimulator()
            val previous = fixture.factory.created.single()
            fixture.factory.load = { null }
            val failedHandler = fixture.controller.startSimulator(true)
            assertTrue(failedHandler.isProcessTerminated)
            assertEquals(SimExitCode.CONFIG_ERROR.int, failedHandler.exitCode)
            assertFalse(oldHandler.isProcessTerminated)
            assertEquals(0, previous.disposeCount)
            assertTrue(fixture.controller.isSimulatorRunning())
            assertSame(oldHandler, fixture.controller.startSimulator())
            assertEquals(1, fixture.factory.created.size)
            assertEquals(2, previous.transport.sent.size)
        }
    }

    @Test
    fun `caller cancellation of a replacement leaves the old runtime usable`() = runBlocking {
        Fixture().use { fixture ->
            val oldHandler = fixture.controller.startSimulator()
            val previous = fixture.factory.created.single()
            fixture.factory.load = { awaitCancellation() }
            val replacement = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.controller.startSimulatorAsync(byDebugger = true)
            }
            replacement.cancelAndJoin()
            assertEquals(0, previous.disposeCount)
            assertTrue(fixture.controller.isSimulatorRunning())
            assertSame(oldHandler, fixture.controller.startSimulator())
            assertFalse(oldHandler.isProcessTerminated)
            assertEquals(1, fixture.factory.created.size)
        }
    }

    @Test
    fun `debugger waits for paused status without resource requests and console attachment preserves startup`() = runBlocking {
        Fixture().use { fixture ->
            val ready = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            val runtime = fixture.factory.created.single()
            assertFalse(ready.isCompleted)
            runtime.transport.receive("sim:pause")
            assertTrue(withTimeout(2_000) { ready.await() })
            val handler = fixture.controller.startSimulator()
            assertFalse(handler.isProcessTerminated)
            assertEquals(1, runtime.transport.sent.size)
            assertEquals(0, fixture.factory.firmwareLoads)
        }
    }

    @Test
    fun `each debugger execution waits for its own acknowledgement`() = runBlocking {
        Fixture().use { fixture ->
            val first = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            val old = fixture.factory.created.single()
            old.transport.receive("sim:pause")
            assertTrue(first.await())
            val second = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            assertEquals(1, old.disposeCount)
            assertFalse(second.isCompleted)
            fixture.factory.created.last().transport.receive("sim:pause")
            assertTrue(withTimeout(2_000) { second.await() })
        }
    }

    @Test
    fun `stop and replacement unblock a debugger readiness wait with failure`() = runBlocking {
        Fixture().use { fixture ->
            val stopped = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            fixture.controller.stopSimulator().join()
            assertFalse(withTimeout(2_000) { stopped.await() })
            val replaced = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            fixture.controller.startSimulator(true)
            assertFalse(withTimeout(2_000) { replaced.await() })
            assertTrue(fixture.controller.isSimulatorRunning())
        }
    }

    @Test
    fun `stop after acknowledgement but before the debugger waiter resumes rejects readiness`() = runBlocking {
        Fixture().use { fixture ->
            val ready = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            fixture.factory.created.single().transport.receive("sim:pause")
            assertFalse(ready.isCompleted)
            fixture.controller.stopSimulator().join()
            assertFalse(withTimeout(2_000) { ready.await() })
        }
    }

    @Test
    fun `replacement after acknowledgement rejects old readiness without stopping the replacement`() = runBlocking {
        Fixture().use { fixture ->
            val ready = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            val previous = fixture.factory.created.single()
            previous.transport.receive("sim:pause")
            assertFalse(ready.isCompleted)
            fixture.controller.startSimulator(true)
            val current = fixture.factory.created.last()
            assertFalse(withTimeout(2_000) { ready.await() })
            assertEquals(1, previous.disposeCount)
            assertEquals(0, current.disposeCount)
            assertTrue(fixture.controller.isSimulatorRunning())
        }
    }

    @Test
    fun `simulator stopping after acknowledgement but before the debugger waiter resumes rejects readiness`() = runBlocking {
        Fixture().use { fixture ->
            val ready = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            val runtime = fixture.factory.created.single()
            runtime.transport.receive("sim:pause")
            assertFalse(ready.isCompleted)
            runtime.transport.receive("sim:stop")
            assertFalse(withTimeout(2_000) { ready.await() })
            assertEquals(1, runtime.disposeCount)
            assertFalse(fixture.controller.isSimulatorRunning())
        }
    }

    @Test
    fun `startup timeout also bounds config loading and cancels the pending request`() = runBlocking {
        Fixture().use { fixture ->
            fixture.factory.load = { awaitCancellation() }
            assertFalse(withTimeout(2_000) { fixture.controller.startDebuggerAndAwaitReady(20) })
            assertTrue(fixture.factory.created.isEmpty())
            assertFalse(fixture.controller.isSimulatorRunning())
            assertEquals(listOf("Wokwi debugger startup timed out"), fixture.notifications)
        }
    }

    @Test
    fun `startup timeout disposes a simulator that never acknowledges readiness`() = runBlocking {
        Fixture().use { fixture ->
            assertFalse(withTimeout(2_000) { fixture.controller.startDebuggerAndAwaitReady(20) })
            assertEquals(1, fixture.factory.created.single().disposeCount)
            assertNull(fixture.gdb.runningPort())
            assertFalse(fixture.controller.isSimulatorRunning())
        }
    }

    @Test
    fun `caller cancellation stops pending debugger startup`() = runBlocking {
        Fixture().use { fixture ->
            fixture.factory.load = { awaitCancellation() }
            val ready = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            ready.cancelAndJoin()
            assertTrue(fixture.factory.created.isEmpty())
            assertFalse(fixture.controller.isSimulatorRunning())
            assertTrue(fixture.notifications.isEmpty())
        }
    }

    @Test
    fun `caller timeout cancels startup without reporting an internal startup timeout`() = runBlocking {
        Fixture().use { fixture ->
            fixture.factory.load = { awaitCancellation() }
            assertNull(withTimeoutOrNull(20) { fixture.controller.startDebuggerAndAwaitReady(2_000) })
            assertFalse(fixture.controller.isSimulatorRunning())
            assertTrue(fixture.notifications.isEmpty())
        }
    }

    @Test
    fun `gdb bind failure prevents browser creation and readiness waiting`() = runBlocking {
        Fixture().use { fixture ->
            fixture.gdb.failBind = true
            assertFalse(withTimeout(2_000) { fixture.controller.startDebuggerAndAwaitReady() })
            assertTrue(fixture.factory.created.isEmpty())
            assertFalse(fixture.controller.isSimulatorRunning())
        }
    }

    @Test
    fun `repeated runtime replacement leaves only the current session job`() = runBlocking {
        Fixture().use { fixture ->
            repeat(5) {
                fixture.controller.startSimulator(true)
                assertEquals(2, fixture.parent.children.count())
            }
            fixture.controller.dispose()
            withTimeout(2_000) { fixture.parent.children.toList().forEach { it.join() } }
            assertTrue(fixture.factory.created.all { it.disposeCount == 1 })
            assertEquals(0, fixture.parent.children.count())
            assertTrue(fixture.parent.isActive)
        }
    }

    @Test
    fun `failed browser construction releases its bound server`() = runBlocking {
        Fixture().use { fixture ->
            fixture.factory.beforeCreate = { throw IllegalStateException("test construction failure") }
            assertFalse(fixture.controller.startSimulatorAsync(byDebugger = true))
            assertNull(fixture.gdb.runningPort())
            assertFalse(fixture.controller.isSimulatorRunning())
        }
    }

    @Test
    fun `dispose prevents late startup and releases owned jobs without cancelling parent`() = runBlocking {
        Fixture().use { fixture ->
            val release = CompletableDeferred<Unit>()
            fixture.factory.load = { debug ->
                withContext(NonCancellable) { release.await() }
                fixture.factory.config(debug)
            }
            val handler = fixture.controller.startSimulator()
            fixture.controller.dispose()
            release.complete(Unit)
            withTimeout(2_000) { fixture.parent.children.toList().forEach { it.join() } }
            assertTrue(handler.isProcessTerminated)
            assertTrue(fixture.factory.created.isEmpty())
            assertTrue(fixture.parent.isActive)
            assertEquals(0, fixture.parent.children.count())
            assertTrue(fixture.controller.startSimulator().isProcessTerminated)
        }
    }

    @Test
    fun `dispose of an active debug runtime fails its readiness wait and releases owned jobs`() = runBlocking {
        Fixture().use { fixture ->
            val ready = async(start = CoroutineStart.UNDISPATCHED) { fixture.controller.startDebuggerAndAwaitReady() }
            val runtime = fixture.factory.created.single()
            fixture.controller.dispose()
            assertFalse(withTimeout(2_000) { ready.await() })
            assertEquals(1, runtime.disposeCount)
            assertFalse(fixture.controller.isSimulatorRunning())
            assertTrue(fixture.parent.isActive)
            assertEquals(0, fixture.parent.children.count())
        }
    }

    private class Fixture(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined) : AutoCloseable {
        val parent = SupervisorJob()
        private val scope = CoroutineScope(parent + dispatcher)
        val factory = FakeFactory(scope)
        val gdb = FakeGdbManager()
        val notifications = mutableListOf<String>()
        val controller = WokwiSessionController(scope, factory, gdb, { RecordingHandler() }, object : UserNotifier {
            override fun notify(title: String, message: String, type: UserNotificationType, action: UserNotificationAction?) {
                notifications.add(title)
            }
        })

        override fun close() {
            controller.dispose()
            parent.cancel()
        }
    }

    /** Runs queued work only when the test permits it, to reproduce an action before its callback executes. */
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    private class FakeFactory(private val scope: CoroutineScope) : SimulationRuntimeFactory {
        private val firmware = FirmwareImage(byteArrayOf(1), FirmwareFormat.BIN, Path.of("firmware.bin"), false,
            1u, listOf(Path.of("firmware.bin")))
        val created = mutableListOf<FakeRuntime>()
        val listeners = mutableListOf<WokwiSession.Listener>()
        var firmwareLoads = 0
        var load: suspend (Boolean) -> LoadedSimulationConfig? = { config(it) }
        var beforeCreate: suspend () -> Unit = {}
        fun config(debug: Boolean) = LoadedSimulationConfig(SimulationConfig("test", "{}", firmware, debug), null)
        override suspend fun loadConfig(waitForDebugger: Boolean) = load(waitForDebugger)
        override suspend fun loadFirmware(config: SimulationConfig): FirmwareImage {
            firmwareLoads++
            return firmware
        }
        override suspend fun ensureBrowserSupported() = true
        override suspend fun createRuntime(config: LoadedSimulationConfig, gdbServer: GdbServer?, listener: WokwiSession.Listener): SimulationRuntime {
            beforeCreate()
            listeners.add(listener)
            val transport = FakeTransport()
            val session = WokwiSession(scope, transport, createStartConfig(config.simulationConfig, config.gdbServerPort),
                ResourceLoader { ByteArray(0) }, gdbServer, listener)
            transport.receive("start")
            return FakeRuntime(config.simulationConfig, session, transport).also(created::add)
        }
    }

    private class FakeRuntime(override var simulationConfig: SimulationConfig, override val session: WokwiSession,
                              val transport: FakeTransport) : SimulationRuntime {
        override val component = JPanel()
        var disposeCount = 0
        override fun dispose() {
            disposeCount++
            session.dispose()
        }
    }

    private class FakeTransport : WokwiTransport {
        private val listeners = mutableListOf<WokwiTransport.Listener>()
        val sent = mutableListOf<String>()
        override fun send(message: String) { sent.add(message) }
        override fun subscribe(listener: WokwiTransport.Listener) { listeners.add(listener) }
        override fun removeSubscriber(listener: WokwiTransport.Listener) { listeners.remove(listener) }
        override fun dispose() { listeners.clear() }
        fun receive(command: String) = listeners.toList().forEach { it.messageReceived("""{"command":"$command"}""") }
    }

    private class FakeGdbManager : GdbServerManager {
        private var port: Int? = null
        var failBind = false
        override fun configure(shouldDebug: Boolean, port: Int?): GdbServer? {
            if (failBind) return null
            this.port = if (shouldDebug) port ?: 3333 else null
            return if (shouldDebug) object : GdbServer {
                override val events: Flow<GdbEvent> = emptyFlow()
                override fun sendResponse(response: String) {}
            } else null
        }
        override fun runningPort() = port
        override fun disposeServer() { port = null }
    }

    private class RecordingHandler : WokwiProcessHandler() {
        val output = mutableListOf<ByteArray>()
        override fun onUartData(bytes: ByteArray) { output.add(bytes) }
        init { startNotify() }
        override fun destroyProcessImpl() { notifyProcessTerminated(0) }
        override fun detachProcessImpl() { notifyProcessDetached() }
        override fun detachIsDefault() = false
        override fun getProcessInput(): OutputStream? = null
        override fun onShutdown(exitCode: SimExitCode) {
            if (!isProcessTerminated) notifyProcessTerminated(exitCode.int)
        }
    }
}
