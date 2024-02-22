package com.github.jozott00.wokwiintellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import javax.swing.Icon
import javax.swing.SwingConstants

object WokwiIcons {

    val Default = IconLoader.getIcon("icons/pluginIcon.svg", WokwiIcons.javaClass)

    val SimulatorToolWindowIcon = IconLoader.getIcon("icons/pluginIcon@13x13.svg", WokwiIcons.javaClass)

    val ConsoleToolWindowIcon = IconLoader.getIcon("icons/logIcon@13x13.svg", WokwiIcons.javaClass)

    val ConfigFile = IconLoader.getIcon("icons/pluginIcon@16x16.svg", WokwiIcons.javaClass)

    val Debug = LayeredIcon(2).also {
        it.setIcon(Default, 0)
        it.setIcon(Overlays.Debug, 1, SwingConstants.SOUTH_EAST)
    }

    object Overlays {
        val Debug: Icon = IconUtil.scale(AllIcons.Actions.StartDebugger, null, 0.8f)
    }

}



