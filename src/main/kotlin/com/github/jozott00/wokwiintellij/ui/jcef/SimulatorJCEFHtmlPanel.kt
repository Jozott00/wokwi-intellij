package com.github.jozott00.wokwiintellij.ui.jcef

import com.github.jozott00.wokwiintellij.jcef.BrowserPipe
import com.github.jozott00.wokwiintellij.jcef.impl.JcefBrowserPipe
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.jcef.JCEFHtmlPanel
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter

class SimulatorJCEFHtmlPanel :
    JCEFHtmlPanel(true, null, null) {

    init {
        val resource = ResourceLoader.loadInternalResource(this.javaClass, "/jcef/simulator/index.html", "text/html")
        super.loadHTML(resource?.content?.toString(Charsets.UTF_8) ?: "<h3>Not Found</h3>")
    }


}