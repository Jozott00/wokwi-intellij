package com.github.jozott00.wokwiintellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient.Properties.JS_QUERY_POOL_SIZE
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.jcef.JCEFHtmlPanel
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JPanel


class JCEFContent(onLoaded: (JCEFContent) -> Unit) : JPanel(), Disposable {

    val browser: JBCefBrowser =
        JCEFHtmlPanel("https://wokwi.com/_alpha/wembed/345144250522927698?partner=espressif&port=9012&data=demo")

    init {
        browser.let {
            layout = BorderLayout()
            add(it.component, BorderLayout.CENTER)
        }
    }

    init {
        browser.jbCefClient.setProperty(JS_QUERY_POOL_SIZE, 5)
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                println("-------------- LOAD END")

                val loadedCallback = JBCefJSQuery.create(browser as JBCefBrowserBase)
                loadedCallback.addHandler { _ ->
                    onLoaded(this@JCEFContent)
                    null
                }

                cefBrowser!!.executeJavaScript(
                    """
                    document.getElementsByTagName("header")[0].style.display = "none";
                    document.body.style.overflow = "hidden";
                    
                    var parentDiv = document.querySelector('.simulation_simulationControls__Jqtsp');
                    var childDivs = parentDiv.children;
                    
                    // Sleep for a little to delay simulator show up
                    setTimeout(function(){
                        ${loadedCallback.inject(null)}
                    }, 300);
                    
                    """.trimIndent(), null, 0
                )

            }
        }, browser.cefBrowser)
    }

    override fun dispose() {
        browser.dispose()
    }
}