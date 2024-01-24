package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.ui.tabs.TabInfo
import java.awt.BorderLayout
import javax.swing.*


class WokwiConsoleToolWindow(project: Project) :
    JPanel() {
    private val tabs: WokwiConsoleTabs = WokwiConsoleTabs(project, project)
    private val controlActionGroup = createControlActionGroup()

    private val actionPlace = "WokwiConsole.topMiddleToolbar"
    private val wrapper = ConsoleWrapper()

    init {
        tabs.apply {
            addTab(createTabInfo("Console", wrapper))
        }

        layout = BorderLayout()
        add(tabs, BorderLayout.CENTER)
    }

    fun setConsole(console: SimulationConsole) {
        wrapper.setConsole(console)
    }

    fun removeConsole() {
        wrapper.removeConsole()
    }

    private fun createTabInfo(title: String, content: JComponent): TabInfo {
        return TabInfo(content).apply {
            text = title
            setActions(controlActionGroup, actionPlace)
        }
    }

    private fun createControlActionGroup(): ActionGroup {
        val am = ActionManager.getInstance()
        return DefaultActionGroup(
            am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiRestartAction"),
            am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiStopAction"),
            am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiWatchAction"),
        )
    }

    private fun createConsoleTab(): TabInfo {
        return TabInfo(JLabel("Console")).apply {
            text = "Console"
            setIcon(AllIcons.Debugger.Console)
            setActions(controlActionGroup, actionPlace)
        }
    }

}

class ConsoleWrapper : JPanel() {
    init {
        layout = BorderLayout()
    }

    fun setConsole(console: SimulationConsole) {
        removeAll()
        add(console)
        repaint()
    }

    fun removeConsole() {
        removeAll()
        repaint()
    }


}

class WokwiConsoleTabs(project: Project, parentDisposable: Disposable) :
    JBRunnerTabs(project, parentDisposable)