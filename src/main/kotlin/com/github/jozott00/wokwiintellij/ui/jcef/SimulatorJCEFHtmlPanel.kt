package com.github.jozott00.wokwiintellij.ui.jcef

import com.github.jozott00.wokwiintellij.jcef.BrowserPipe
import com.github.jozott00.wokwiintellij.jcef.addLoadHandler
import com.github.jozott00.wokwiintellij.jcef.impl.JcefBrowserPipe
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.ui.util.preferredWidth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar

class SimulatorJCEFHtmlPanel(parentDisposable: Disposable) : ComponentContainer {

    private val browser = JCEFHtmlPanel(true, null, null)
    val browserPipe: BrowserPipe = JcefBrowserPipe(browser, this)

    private val loadHandler = LoadHandler(this)

    private val contentCardLayout = CardLayout()
    private val contentCard = JPanel(contentCardLayout).also {
        it.add("LOADING", buildLoadingPanel())
        it.add("BROWSER", browser.component)
    }


    init {
        Disposer.register(parentDisposable, this)
        Disposer.register(this, browser)

        browser.addLoadHandler(loadHandler, this)
        browserPipe.subscribe("meta", loadHandler)

        val resource = ResourceLoader.loadInternalResource(this.javaClass, "/jcef/simulator/index.html", "text/html")
        browser.loadHTML(resource?.content?.toString(Charsets.UTF_8) ?: "<h3>Not Found</h3>")
    }


    override fun dispose() {
    }

    override fun getComponent() = contentCard

    override fun getPreferredFocusableComponent(): JComponent {
        return contentCard
    }

    private fun buildLoadingPanel() = panel {
        row {
            panel {
                row {
                    text("Loading simulator...")
                }
                    .bottomGap(BottomGap.NONE)
                row {
                    cell(JProgressBar().also {
                        it.isIndeterminate = true
                        it.preferredWidth = 300
                    })
                }
                    .topGap(TopGap.NONE)
            }
                .align(Align.CENTER)
        }
    }


    private class LoadHandler(val panel: SimulatorJCEFHtmlPanel) : CefLoadHandlerAdapter(), BrowserPipe.Subscriber {
        override fun onLoadError(
            browser: CefBrowser?,
            frame: CefFrame?,
            errorCode: CefLoadHandler.ErrorCode?,
            errorText: String?,
            failedUrl: String?
        ) {
            thisLogger().warn("LoadError: $errorCode, $errorText")

            val errorDescription =
                if (errorCode == CefLoadHandler.ErrorCode.ERR_INTERNET_DISCONNECTED)
                    "No connection to the internet." else
                    "Unknown Error: $errorText"

            invokeLater {
                panel.contentCard.removeAll()
                panel.contentCard.add(createErrorPanel(errorDescription))
            }

        }

        override fun messageReceived(data: String): Boolean {
            val json = Json.parseToJsonElement(data).jsonObject

            val type: String = json["msg"]?.jsonPrimitive?.content ?: run {
                thisLogger().error("Malformed data received: $data", Throwable())
                return false
            }

            when (type) {
                "frameLoaded" -> {
                    invokeLater { panel.contentCardLayout.show(panel.contentCard, "BROWSER") }
                }

                else -> {
                    thisLogger().error("Meta message '$type' not supported.", Throwable())
                    return false
                }
            }

            return true
        }

        fun createErrorPanel(errorText: String) = panel {
            row {
                icon(AllIcons.General.ErrorDialog)
                    .align(Align.CENTER)
            }
            row {
                label("Failed to load simulator")
                    .bold()
                    .align(Align.CENTER)
            }
            row {
                label(errorText)
                    .align(Align.CENTER)
            }
        }

    }
}