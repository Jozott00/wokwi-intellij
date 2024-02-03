package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.listeners.WokwiElfFileListener
import com.github.jozott00.wokwiintellij.states.WokwiConfigState
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection

@Service(Service.Level.PROJECT)
class WokwiProjectServiceOld(val project: Project) : Disposable {

    private val componentService = project.service<WokwiComponentService>()
    private val configState = project.service<WokwiConfigState>()
    private val dataService = project.service<WokwiDataService>()

    private var msgBusConnection: MessageBusConnection? = null
    private var simulationRunning = false;

    fun startSimulator() {
        if (dataService.retrieveImage() == null) {
            return
        }

//        componentService.toolWindow.showSimulation()
        simulationRunning = true
        watchStart()
    }

    fun stopSimulator() {
        componentService.simulatorToolWindow.showConfig()
        simulationRunning = false
        watchStop()
    }

    fun startup() {
        val port = 9012 // Specify your port here
    }

    override fun dispose() {
    }

    fun restartSimulation() {

    }

    fun elfFileUpdate() {
        WokwiNotifier.notifyBalloon("New build available, restarting simulation...", project)

    }

    fun watchStart() {
        if (!configState.watchElf || !simulationRunning) return
        msgBusConnection = project.messageBus.connect()
        msgBusConnection?.subscribe(VirtualFileManager.VFS_CHANGES, WokwiElfFileListener(project))
    }

    fun watchStop() {
        msgBusConnection?.disconnect()
        msgBusConnection = null
    }


}