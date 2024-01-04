package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.listeners.WokwiElfFileListener
import com.github.jozott00.wokwiintellij.states.WokwiConfigState
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.github.jozott00.wokwiintellij.wokwiServer.WokwiServer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection

@Service(Service.Level.PROJECT)
class WokwiProjectService(val project: Project) : Disposable {
    private var server: WokwiServer? = null

    private val componentService = project.service<WokwiComponentService>()
    private val configState = project.service<WokwiConfigState>()
    private val dataService = project.service<WokwiDataService>()
    private val simulationService = project.service<WokwiSimulationService>()

    private var msgBusConnection: MessageBusConnection? = null
    private var simulationRunning = false;
    fun startSimulator() {
        if (dataService.retrieveImage() == null) {
            return
        }

        componentService.toolWindow.showSimulation()
        simulationRunning = true
        watchStart()
    }

    fun stopSimulator() {
        componentService.toolWindow.showConfig()
        simulationRunning = false
        watchStop()
    }

    fun startup() {
        val port = 9012 // Specify your port here
        server = WokwiServer(port, project).apply {
            start()
            println("WokwiServer started on port: $port")
        }
    }

    override fun dispose() {
        server?.stop()
    }

    fun restartSimulation() {
        simulationService.restartAll()
    }

    fun elfFileUpdate() {
        println("FILE UPDATED ... restart")
        WokwiNotifier.notifyBalloon("New build available, restarting simulation...", project)
        simulationService.restartAll()
    }

    fun watchStart() {
        if (!configState.watchElf || !simulationRunning) return
        println("START WATCHING")
        msgBusConnection = project.messageBus.connect()
        msgBusConnection?.subscribe(VirtualFileManager.VFS_CHANGES, WokwiElfFileListener(project))
    }

    fun watchStop() {
        println("STOP WATCHING")
        msgBusConnection?.disconnect()
        msgBusConnection = null
    }


}