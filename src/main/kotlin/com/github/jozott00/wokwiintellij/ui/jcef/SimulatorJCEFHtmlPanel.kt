package com.github.jozott00.wokwiintellij.ui.jcef

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
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar

class SimulatorJCEFHtmlPanel(parentDisposable: Disposable) : ComponentContainer {

    private val browser = JCEFHtmlPanel(true, null, null)
    private val contentCardLayout = CardLayout()
    private val contentCard = JPanel(contentCardLayout).also {
        it.add("LOADING", buildLoadingPanel())
        it.add("BROWSER", browser.component)
    }
    val wokwiTransport = JcefWokwiTransport(browser) {
        invokeLater { contentCardLayout.show(contentCard, "BROWSER") }
    }


    init {
        Disposer.register(parentDisposable, this)
        Disposer.register(this, browser)
        Disposer.register(this, wokwiTransport)

        browser.addLoadHandler(LoadHandler(this), this)

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
                        it.preferredSize = Dimension(300, it.preferredSize.height)
                    })
                }
                    .topGap(TopGap.NONE)
            }
                .align(Align.CENTER)
        }
    }


    private class LoadHandler(val panel: SimulatorJCEFHtmlPanel) : CefLoadHandlerAdapter() {
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
