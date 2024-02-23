package com.github.jozott00.wokwiintellij.simulator


import com.github.jozott00.wokwiintellij.jcef.BrowserPipe
import com.github.jozott00.wokwiintellij.simulator.args.FirmwareFormat
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgsFirmware
import com.github.jozott00.wokwiintellij.simulator.gdb.GDBServerCommunicator
import com.github.jozott00.wokwiintellij.simulator.gdb.GDBServerEvent
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import io.ktor.util.*
import kotlinx.serialization.json.*
import java.net.URL
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val LOG = logger<WokwiSimulator>()

class WokwiSimulator(
    private val runArgs: WokwiArgs,
    parentDisposable: Disposable,
) : Disposable,
    Simulator,
    ComponentContainer,
    BrowserPipe.Subscriber {

    private var browserReady = false
    private var startInvoked = false

    private val browser = SimulatorJCEFHtmlPanel(this)
    private val browserPipe = browser.browserPipe

    private val myEventMulticaster = createEventMulticaster()
    private val myListeners: MutableList<WokwiSimulatorListener> = ContainerUtil.createLockFreeCopyOnWriteList()

    private val ansiEscapeDecoder = AnsiEscapeDecoder()
    private var gdbServer: GDBServerCommunicator? = null

    private var simulationRunning = false

    init {
        Disposer.register(parentDisposable, this)
        browserPipe.subscribe(PIPE_TOPIC, this, this)
    }

    override fun start() {
        startInvoked = true
        startInternal()
    }

    private fun startInternal() {
        simulationRunning = false

        // if browser not yet ready just return
        if (!browserReady) return
        if (!startInvoked) return

        LOG.info("(Re)starting simulation...")

        @OptIn(ExperimentalEncodingApi::class)
        val firmwareString = Base64.encode(runArgs.firmware.buffer)

        val cmd = Command.start(
            diagram = runArgs.diagram,
            firmware = firmwareString,
            firmwareFormat = runArgs.firmware.format,
            license = runArgs.license,
            waitForDebugger = runArgs.waitForDebugger
        )
        browserPipe.send(PIPE_TOPIC, cmd)
        myEventMulticaster.onStarted(runArgs)
    }

    override fun setFirmware(firmware: WokwiArgsFirmware) {
        runArgs.firmware = firmware
    }

    override fun getFirmware(): WokwiArgsFirmware {
        return runArgs.firmware
    }

    override suspend fun connectToGDBServer(server: GDBServerCommunicator) {
        gdbServer = server
        server.getMessageFlow().collect { event ->
            when (event) {
                is GDBServerEvent.Connected -> {}
                is GDBServerEvent.Error -> LOG.error("Error: ${event.error}")
                is GDBServerEvent.Message -> {
                    browserPipe.send(PIPE_TOPIC, Command.gdbMessage(event.message))
                }
                is GDBServerEvent.Break -> browserPipe.send(PIPE_TOPIC, Command.gdbBreak())
            }
        }
    }

    private fun startRecv() {
        browserReady = true
        startInternal()
    }

    private fun uartDataRecv(data: JsonObject) {
        val bytes = data["bytes"]
            ?.jsonArray
            ?.map { it.jsonPrimitive.int.toByte() }
            ?.toByteArray() ?: run {
            LOG.error("Malformed data received: No bytes: $data");
            return
        }

        if (bytes.isEmpty()) return

        val str = String(bytes, Charsets.UTF_8)

        ansiEscapeDecoder.escapeText(str, ProcessOutputTypes.STDOUT) { t, contentType ->
            myEventMulticaster.onTextAvailable(t, contentType)
        }
    }

    private fun loadResourceRecv(req: JsonObject) {
        // TODO: Make this offline
        val urlString = req["url"]?.jsonPrimitive?.content ?: run {
            LOG.error("Malformed data received: No url: $req");
            return
        }
        val url = URL(urlString)
        val resource = url.readBytes().encodeBase64()
        val cmd = Command.resourceData(resource)
        browserPipe.send(PIPE_TOPIC, cmd)

        checkSimulationStartedRunning()
    }

    private fun gdbResponseRecv(req: JsonObject) {
        val response = req["response"]?.jsonPrimitive?.content ?: run {
            LOG.error("Malformed data received: No response field: $req");
            return
        }
        gdbServer?.sendResponse(response)
    }

    override fun messageReceived(data: String): Boolean {
        val json = Json.parseToJsonElement(data).jsonObject

        val type: String = json["command"]?.jsonPrimitive?.content ?: run {
            LOG.error("Malformed data received: $data");
            return false
        }

        when (type) {
            "start" -> startRecv()
            "loadResource" -> loadResourceRecv(json)
            "uartData" -> uartDataRecv(json) // do nothing right now
            "wifiFrame", "wifiConnect" -> {
                TODO("Not yet implemented")
            } // do nothing right now
            "gdbResponse" -> gdbResponseRecv(json)
            else -> {
                LOG.warn("Unknown command: $type")
                LOG.debug("Unknown command data: $data")
                return false
            }
        }

        return true
    }

    private fun checkSimulationStartedRunning() {
        if (!simulationRunning) {
            simulationRunning = true
            myEventMulticaster.onRunning()
        }
    }

    companion object {
        private val PIPE_TOPIC = "wokwi"
    }

    fun addSimulatorListener(listener: WokwiSimulatorListener) {
        myListeners.add(listener)
    }

    override fun dispose() {
        createEventMulticaster().onShutdown()
        myListeners.clear()
    }

    override fun getComponent() = browser.component

    override fun getPreferredFocusableComponent() = component


    private fun createEventMulticaster(): WokwiSimulatorListener {
        return object : WokwiSimulatorListener {
            override fun onStarted(runArgs: WokwiArgs) {
                notifyAll { it.onStarted(runArgs) }
            }

            override fun onShutdown() {
                notifyAll { it.onShutdown() }
            }

            override fun onTextAvailable(text: String, outputType: Key<*>) {
                notifyAll { it.onTextAvailable(text, outputType) }
            }

            override fun onRunning() {
                notifyAll { it.onRunning() }
            }

            private fun notifyAll(m: (WokwiSimulatorListener) -> Unit) {
                for (l in myListeners) {
                    m(l)
                }
            }

        }
    }

}