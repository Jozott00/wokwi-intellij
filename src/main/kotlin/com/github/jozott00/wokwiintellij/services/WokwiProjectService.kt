package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.github.jozott00.wokwiintellij.utils.ToolWindowUtils
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.readBytes
import com.intellij.openapi.vfs.readText

@Service(Service.Level.PROJECT)
class WokwiProjectService(val project: Project) : Disposable {

    private var simulator: WokwiSimulator? = null

    private val componentService = project.service<WokwiComponentService>()

    fun startSimulator() {
        val config =
            WokwiConfigProcessor.loadConfig(
                project,
                WokwiConstants.WOKWI_CONFIG_FILE,
                WokwiConstants.WOKWI_DIAGRAM_FILE
            )
                ?: return
        val args = buildArgs(config)

        simulator?.disposeByDisposer()
        val browser = SimulatorJCEFHtmlPanel()
        val console = SimulationConsole(project)
        simulator = WokwiSimulator(browser, console)

        simulator?.start(args)
        ToolWindowUtils.setSimulatorIcon(project, true)
        componentService.simulatorToolWindow.showSimulation(browser.component)
        componentService.consoleToolWindow.setConsole(console)

    }

    fun restartSimulation() {
        simulator?.run {
            restart()
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

    private fun buildArgs(config: WokwiConfig): WokwiSimulator.RunArgs {
        val diagram = config.diagram.readText()
        // TODO: read elf and process
        val firmware = config.firmware.readBytes()
        return WokwiSimulator.RunArgs(diagram, firmware)
    }


    companion object {
        private val LOG = logger<WokwiProjectService>()
    }

}