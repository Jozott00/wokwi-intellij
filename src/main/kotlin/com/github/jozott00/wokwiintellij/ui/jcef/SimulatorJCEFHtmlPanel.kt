package com.github.jozott00.wokwiintellij.ui.jcef

import com.intellij.ui.jcef.JCEFHtmlPanel

class SimulatorJCEFHtmlPanel :
    JCEFHtmlPanel(true, null, null) {

    init {
        val resource = ResourceLoader.loadInternalResource(this.javaClass, "/jcef/simulator/index.html", "text/html")
        super.loadHTML(resource?.content?.toString(Charsets.UTF_8) ?: "<h3>Not Found</h3>")
    }


}