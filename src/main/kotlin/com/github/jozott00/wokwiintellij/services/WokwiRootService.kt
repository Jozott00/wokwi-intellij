package com.github.jozott00.wokwiintellij.services

import com.beust.klaxon.json
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class WokwiRootService : Disposable {

    private var simulation: WokwiSimulator? = null

    fun newSimulation(): JComponent {
        simulation?.dispose()
        val browser = SimulatorJCEFHtmlPanel()
        simulation = WokwiSimulator(browser)

        val firmware = getDefaultImage()
        val diagram = getDefaultDiagram()
        simulation?.start(WokwiSimulator.RunArgs(diagram, firmware))
        return browser.component
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

    private fun getDefaultImage(): ByteArray {
        val image = this.javaClass.getResourceAsStream("/tests/golioth-basics")?.readAllBytes()
        checkNotNull(image) { "Couldn't load default image!" }
        return image
    }

    private fun getDefaultDiagram(): String {
        val diagram = this.javaClass.getResourceAsStream("/tests/diagram.json")?.readAllBytes()
        checkNotNull(diagram) { "Couldn't load default diagram!" }
        return diagram.decodeToString()
    }

}