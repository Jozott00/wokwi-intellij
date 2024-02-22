package com.github.jozott00.wokwiintellij.ui.jcef

import com.github.jozott00.wokwiintellij.jcef.impl.JcefBrowserPipe
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JCEFHtmlPanel

class SimulatorJCEFHtmlPanel(parentDisposable: Disposable) :
    JCEFHtmlPanel(true, null, null) {

        val browserPipe = JcefBrowserPipe(this, this)

    init {
        Disposer.register(parentDisposable, this)
        val resource = ResourceLoader.loadInternalResource(this.javaClass, "/jcef/simulator/index.html", "text/html")
        super.loadHTML(resource?.content?.toString(Charsets.UTF_8) ?: "<h3>Not Found</h3>")
    }


}