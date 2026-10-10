package com.github.jozott00.wokwiintellij.ui.jcef

import com.github.jozott00.wokwiintellij.core.ports.WokwiTransport
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.util.concurrent.CopyOnWriteArrayList

class JcefWokwiTransport(
    private val browser: JBCefBrowser,
    private val onFrameLoaded: () -> Unit,
) : WokwiTransport, Disposable, CefLoadHandlerAdapter() {

    private val listeners = CopyOnWriteArrayList<WokwiTransport.Listener>()
    private val injectQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private var disposed = false

    init {
        Disposer.register(this, injectQuery)
        injectQuery.addHandler(::onReceive)
        browser.addLoadHandler(this, this)
    }

    override fun send(message: String) {
        if (disposed) return

        val funCall = """
            window.$NAMESPACE_IN_BROWSER.$RECEIVE_MESSAGE_FROM_INTELLIJ_FUNC("$WOKWI_TOPIC", $message);
        """.trimIndent()

        browser.executeJavaScript(funCall)
    }

    override fun subscribe(listener: WokwiTransport.Listener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeSubscriber(listener: WokwiTransport.Listener) {
        listeners.remove(listener)
    }

    override fun dispose() {
        disposed = true
        listeners.clear()
    }

    override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
        val code = """
            window.$NAMESPACE_IN_BROWSER.$POST_MESSAGE_FROM_INTELLIJ_FUNC = data => ${injectQuery.inject("data")};
        """.trimIndent()

        browser?.executeJavaScript(code, null, 0)
        browser?.executeJavaScript("window.dispatchEvent(new Event('IdeReady'));", null, 0)
    }

    @Suppress("SameReturnValue")
    private fun onReceive(rawMessage: String): JBCefJSQuery.Response? {
        val message = parseMessage(rawMessage) ?: return null

        when (message.type) {
            WOKWI_TOPIC -> informSubscribers(message.data)
            META_TOPIC -> handleMetaMessage(message.data)
            else -> LOG.warn("Unsupported JCEF transport message type: ${message.type}\nAttached data: ${message.data}")
        }

        return null
    }

    private fun informSubscribers(message: String): Boolean {
        for (listener in listeners) {
            if (!listener.messageReceived(message)) {
                return false
            }
        }

        return true
    }

    private fun handleMetaMessage(data: String) {
        val type = try {
            Json.parseToJsonElement(data).jsonObject["msg"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            LOG.error("Malformed meta message received: $data", e)
            null
        }

        when (type) {
            "frameLoaded" -> onFrameLoaded()
            null -> Unit
            else -> LOG.warn("Unsupported meta message: $type")
        }
    }

    private fun parseMessage(json: String): MessageObj? {
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            LOG.error("Malformed JCEF transport message received: $json", e)
            null
        }
    }

    companion object {
        private val LOG = logger<JcefWokwiTransport>()

        private const val NAMESPACE_IN_BROWSER = "__WokwiIntellij"
        private const val POST_MESSAGE_FROM_INTELLIJ_FUNC = "__postMessageToPipe"
        private const val RECEIVE_MESSAGE_FROM_INTELLIJ_FUNC = "__receiveMessageFromPipe"
        private const val WOKWI_TOPIC = "wokwi"
        private const val META_TOPIC = "meta"
    }

    @Serializable
    private data class MessageObj(
        val type: String,
        @Serializable(with = RawJsonSerializer::class) val data: String,
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Serializer(forClass = String::class)
    private object RawJsonSerializer : KSerializer<String> {
        override fun serialize(encoder: Encoder, value: String) {
            encoder.encodeString(value)
        }

        override fun deserialize(decoder: Decoder): String {
            return decoder.decodeSerializableValue(JsonElement.serializer()).toString()
        }
    }
}
