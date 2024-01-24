package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.github.jozott00.wokwiintellij.utils.ToolWindowUtils
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

@Service(Service.Level.PROJECT)
class WokwiProjectService(val project: Project) : Disposable {

    private var simulator: WokwiSimulator? = null

    private val componentService = project.service<WokwiComponentService>()
    private val defaultRunArgs = WokwiSimulator.RunArgs(getDefaultDiagram(), getDefaultImage())

    fun startSimulator() {
        simulator?.disposeByDisposer()
        val browser = SimulatorJCEFHtmlPanel()
        val console = SimulationConsole(project)
        simulator = WokwiSimulator(browser, console)

        simulator?.start(defaultRunArgs)
        ToolWindowUtils.setSimulatorIcon(project, true)
        componentService.simulatorToolWindow.showSimulation(browser.component)
        componentService.consoleToolWindow.setConsole(console)

    }

    fun restartSimulation() {
        simulator?.run {
            start(defaultRunArgs)
            return
        }

        startSimulator()
    }

    fun stopSimulator() {
        simulator?.disposeByDisposer()
        simulator = null
        ToolWindowUtils.setSimulatorIcon(project, false)
        componentService.simulatorToolWindow.showConfig()
        componentService.consoleToolWindow.removeConsole()
    }


    override fun dispose() {
        simulator?.disposeByDisposer()
    }

    fun elfFileUpdate() {
        WokwiNotifier.notifyBalloon("New build available, restarting simulation...", project)
    }

    fun watchStart() {
        LOG.info("watchStart() invoked")
    }

    fun watchStop() {
        LOG.info("watchStop() invoked")
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

    companion object {
        private val LOG = logger<WokwiProjectService>()
    }

}