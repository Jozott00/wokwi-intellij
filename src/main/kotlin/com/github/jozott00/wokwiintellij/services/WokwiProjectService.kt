package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.simulator.WokwiSimulator
import com.github.jozott00.wokwiintellij.toml.WokwiConfigProcessor
import com.github.jozott00.wokwiintellij.toolWindow.ConsoleWindowFactory
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.github.jozott00.wokwiintellij.utils.ToolWindowUtils
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager

@Service(Service.Level.PROJECT)
class WokwiProjectService(val project: Project) : Disposable {

    private var simulator: WokwiSimulator? = null

    private val componentService = project.service<WokwiComponentService>()
    private val argsLoader = project.service<WokwiArgsLoader>()
    private var consoleToolWindow: ToolWindow? = null

    fun startSimulator() {
        LOG.info("Start simulator...")
        activateConsoleToolWindow()
        val config =
            WokwiConfigProcessor.loadConfig(
                project,
                WokwiConstants.WOKWI_CONFIG_FILE,
                WokwiConstants.WOKWI_DIAGRAM_FILE
            )
                ?: return
        val args = argsLoader.load(config) ?: return

        simulator?.disposeByDisposer()
        val browser = SimulatorJCEFHtmlPanel()
        val console = SimulationConsole(project)
        simulator = WokwiSimulator(args, browser, console)
        simulator?.start()
        ToolWindowUtils.setSimulatorIcon(project, true)
        componentService.simulatorToolWindowComponent.showSimulation(browser.component)
        componentService.consoleToolWindowComponent.setConsole(console)
    }

    fun restartSimulation() {
        simulator?.run {
            start()
            return
        }

        startSimulator()
    }

    fun stopSimulator() {
        simulator?.disposeByDisposer()
        simulator = null

        ToolWindowUtils.setSimulatorIcon(project, false)
        componentService.simulatorToolWindowComponent.showConfig()
        componentService.consoleToolWindowComponent.removeConsole()
    }


    override fun dispose() {
        simulator?.disposeByDisposer()
    }

    fun firmwareUpdated() {
        WokwiNotifier.notifyBalloon(title = "New firmware detected", "Restarting Wokwi simulator...")
        simulator?.let {
            val firmware = it.getFirmware().rootFile
            val newFirmware = argsLoader.loadFirmware(firmware) ?: return
            it.setFirmware(newFirmware)
            it.start()
        }
    }

    fun getWatchPaths(): List<String>? {
        return simulator?.getFirmware()?.binaryPaths
    }

    private fun activateConsoleToolWindow() {
        consoleToolWindow?.let {
            it.show()
            return
        }
        consoleToolWindow =
            ToolWindowManager.getInstance(project)
                .registerToolWindow("Wokwi Run") {
                    val factory = ConsoleWindowFactory()
                    contentFactory = factory
                    icon = WokwiIcons.ConsoleToolWindowIcon
                    canCloseContent = false
                }
    }


    companion object {
        private val LOG = logger<WokwiProjectService>()
    }

}