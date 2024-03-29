package com.github.jozott00.wokwiintellij.jcef.impl

import com.github.jozott00.wokwiintellij.jcef.BrowserPipe
import com.github.jozott00.wokwiintellij.jcef.addLoadHandler
import com.github.jozott00.wokwiintellij.jcef.executeJavaScript
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
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class JcefBrowserPipe(private val browser: JBCefBrowser, parentDisposable: Disposable) : BrowserPipe, CefLoadHandlerAdapter() {

    // subscribers to specific types
    private val subscribers = hashMapOf<String, MutableList<BrowserPipe.Subscriber>>()

    private val injectQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

    init {
        Disposer.register(parentDisposable, this)
        Disposer.register(this, injectQuery)
        injectQuery.addHandler(::onReceive)
        browser.addLoadHandler(this, this)
    }

    override fun send(type: String, data: String) {
        val funCall = """
            window.$NAMESPACE_IN_BROWSER.$RECEIVE_MESSAGE_FROM_INTELLIJ_FUNC("$type", $data);
        """.trimIndent()

        browser.executeJavaScript(funCall)
    }

    override fun subscribe(type: String, subscriber: BrowserPipe.Subscriber) {
        subscribers.merge(type, mutableListOf(subscriber)) { current, _ ->
            current.also { it.add(subscriber) }
        }
    }

    override fun removeSubscriber(type: String, subscriber: BrowserPipe.Subscriber) {
        subscribers[type]?.remove(subscriber)
        if (subscribers[type]?.isEmpty() == true) {
            subscribers.remove(type)
        }
    }

    override fun dispose() {
        subscribers.clear()

    }

    // inject code to browser
    override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
        val code = """
            window.$NAMESPACE_IN_BROWSER.$POST_MESSAGE_FROM_INTELLIJ_FUNC = data => ${injectQuery.inject("data")};
        """.trimIndent()

        browser?.executeJavaScript(code, null, 0)
        browser?.executeJavaScript("window.dispatchEvent(new Event('IdeReady'));", null, 0)
    }

    @Suppress("SameReturnValue")
    private fun onReceive(msg: String): JBCefJSQuery.Response? {
        val (type, data) = msg.let(::parseObj) ?: return null
        informSubscribers(type, data)
        return null
    }

    private fun informSubscribers(type: String, data: String) {
        when (val subs = subscribers[type]) {
            null -> logger.warn("No subscribers for $type!\nAttached data: $data")
            else -> subs.takeWhile { it.messageReceived(data) }
        }
    }

    private fun parseObj(json: String): MessageObj? {
        try {
            return Json.decodeFromString(json)
        } catch (e: Exception) {
            logger.error(e)
            return null
        }
    }

    companion object {
        val logger = logger<JcefBrowserPipe>()

        const val NAMESPACE_IN_BROWSER = "__WokwiIntellij"
        const val POST_MESSAGE_FROM_INTELLIJ_FUNC = "__postMessageToPipe"
        const val RECEIVE_MESSAGE_FROM_INTELLIJ_FUNC = "__receiveMessageFromPipe"

    }


    @Serializable
    private data class MessageObj(
        val type: String,
        @Serializable(with = RawJsonSerializer::class) val data: String
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