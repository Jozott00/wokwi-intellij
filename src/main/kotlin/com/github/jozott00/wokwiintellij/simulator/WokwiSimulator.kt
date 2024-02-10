package com.github.jozott00.wokwiintellij.simulator


import com.github.jozott00.wokwiintellij.jcef.BrowserPipe
import com.github.jozott00.wokwiintellij.jcef.impl.JcefBrowserPipe
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgsFirmware
import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import io.ktor.util.*
import kotlinx.serialization.json.*
import java.net.URL
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val LOG = logger<WokwiSimulator>()

class WokwiSimulator(
    private val runArgs: WokwiArgs,
    private val browser: SimulatorJCEFHtmlPanel,
    private val console: SimulationConsole
) : Disposable,
    Simulator,
    BrowserPipe.Subscriber {

    private var browserReady = false
    private val browserPipe = JcefBrowserPipe(browser)

    init {
        Disposer.register(this, browser)
        Disposer.register(this, console)
        Disposer.register(browser, browserPipe)
        browserPipe.subscribe(PIPE_TOPIC, this, this)
    }

    override fun start() {
        LOG.info("(Re)starting simulation...")
        // if browser not yet ready just return
        if (!browserReady) return

        console.clear()

        @OptIn(ExperimentalEncodingApi::class)
        val firmwareString = Base64.encode(runArgs.firmware.buffer)


        val cmd = Command.start(runArgs.diagram, firmwareString, runArgs.license)
        browserPipe.send(PIPE_TOPIC, cmd)
    }

    override fun setFirmware(firmware: WokwiArgsFirmware) {
        runArgs.firmware = firmware
    }

    override fun getFirmware(): WokwiArgsFirmware {
        return runArgs.firmware
    }

    private fun startRecv() {
        browserReady = true

        start()
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

        console.appendLog(bytes)
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
            else -> {
                LOG.warn("Unknown command: $type")
                LOG.debug("Unknown command data: $data")
                return false
            }
        }

        return true
    }

    companion object {
        private val PIPE_TOPIC = "wokwi"
    }

    override fun dispose() {
        // Nothing to do
    }

}