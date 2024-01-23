package com.github.jozott00.wokwiintellij.simulator


import com.github.jozott00.wokwiintellij.jcef.BrowserPipe
import com.github.jozott00.wokwiintellij.jcef.impl.JcefBrowserPipe
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import io.ktor.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val LOG = logger<WokwiSimulator>()

class WokwiSimulator(private val browser: SimulatorJCEFHtmlPanel) : Disposable, BrowserPipe.Subscriber {
    private val browserPipe = JcefBrowserPipe(browser)

    private var browserReady = false;
    private var runArgs: RunArgs? = null;

    init {
        Disposer.register(this, browser)
        Disposer.register(browser, browserPipe)
        browserPipe.subscribe(PIPE_TOPIC, this, this)
    }

    fun start(args: RunArgs) {
        runArgs = args

        // if browser not yet ready just return
        if (!browserReady) return

        @OptIn(ExperimentalEncodingApi::class)
        val firmwareString = runArgs?.firmware?.let { Base64.encode(args.firmware) } ?: ""

        // TODO: REMOVE!
        val license = System.getProperty("WOKWI_LICENSE")

        val cmd = Command.start(args.diagram, firmwareString, license)
        browserPipe.send(PIPE_TOPIC, cmd)
    }

    private fun loadResourceRecv(req: JsonObject) {
        // TODO: Make this offline
        val urlString = req["url"]?.jsonPrimitive?.content ?: run {
//            logger.error("Malformed data received: No url: $req");
            return
        }
        val url = URL(urlString)
        val resource = url.readBytes().encodeBase64()
        val cmd = Command.resourceData(resource)
        browserPipe.send(PIPE_TOPIC, cmd)
    }

    private fun wifiConnectRecv() {

    }

    private fun startRecv() {
        LOG.info("Starting simulator...")
        browserReady = true

        // if run args where already provided start with them
        runArgs?.let { start(it) }
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
            "uartData" -> {} // do nothing right now
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

    override fun dispose() {}

    companion object {
        private val PIPE_TOPIC = "wokwi"
    }


    class RunArgs(
        val diagram: String,
        val firmware: ByteArray,
    )

}