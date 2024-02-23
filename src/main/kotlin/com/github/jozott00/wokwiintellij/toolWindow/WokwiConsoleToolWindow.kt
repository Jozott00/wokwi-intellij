package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.extensions.wokwiDisposable
import com.github.jozott00.wokwiintellij.ui.console.SimulationConsole
import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.tabs.TabInfo
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel


@Suppress("SameParameterValue")
class WokwiConsoleToolWindow(project: Project) :
    JPanel() {
    private val tabs: WokwiConsoleTabs = WokwiConsoleTabs(project, project.wokwiDisposable)
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

    private fun createTabInfo(title: String, content: JComponent): TabInfo {
        return TabInfo(content).apply {
            text = title
            setActions(controlActionGroup, actionPlace)
        }
    }

    private fun createControlActionGroup(): ActionGroup {
        val am = ActionManager.getInstance()
        return DefaultActionGroup().apply {
            add(am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiRestartAction"))
            add(am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiStopAction"))
            addSeparator()
            add(am.getAction("com.github.jozott00.wokwiintellij.actions.WokwiWatchAction"))
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
}
