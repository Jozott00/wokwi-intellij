package com.github.jozott00.wokwiintellij.ui.jcef

import com.github.jozott00.wokwiintellij.WokwiConstants
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WokwiHtmlPageFactory(
    private val resourceClass: Class<*> = WokwiHtmlPageFactory::class.java,
) {

    fun createPage(options: Options = Options()): String {
        val template = loadText(SIMULATOR_HTML)
        val css = loadText(BRIDGE_CSS)
        val js = loadText(BRIDGE_JS)

        return template
            .replace("{{WOKWI_IFRAME_URL}}", buildWokwiUrl(options))
            .replace("{{BRIDGE_CSS}}", css)
            .replace("{{BRIDGE_JS}}", js)
    }

    private fun buildWokwiUrl(options: Options): String {
        val params = buildList {
            add("v=${urlEncode(options.extensionVersion)}")
            options.gitHash?.takeIf { it.isNotBlank() }?.let { add("g=${urlEncode(it)}") }
            options.licenseUserId?.takeIf { it.isNotBlank() }?.let { add("u=${urlEncode(it)}") }
        }

        return "${options.wokwiHost.trimEnd('/')}/vscode/wcode?${params.joinToString("&")}"
    }

    private fun loadText(path: String): String {
        return ResourceLoader.loadInternalResource(resourceClass, path, null)
            ?.content
            ?.toString(Charsets.UTF_8)
            ?: error("Missing Wokwi wrapper resource: $path")
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    data class Options(
        val wokwiHost: String = DEFAULT_WOKWI_HOST,
        val extensionVersion: String = WokwiConstants.WOKWI_WCODE_VERSION,
        val gitHash: String? = null,
        val licenseUserId: String? = null,
    )

    companion object {
        const val DEFAULT_WOKWI_HOST = "https://wokwi.com"

        private const val SIMULATOR_HTML = "/wokwi/wrapper/simulator.html"
        private const val BRIDGE_CSS = "/wokwi/wrapper/bridge.css"
        private const val BRIDGE_JS = "/wokwi/wrapper/bridge.js"
    }
}
