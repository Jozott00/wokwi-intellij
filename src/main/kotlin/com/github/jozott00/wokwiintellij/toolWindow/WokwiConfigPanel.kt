package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.states.ESPDevice
import com.github.jozott00.wokwiintellij.states.FlashSize
import com.github.jozott00.wokwiintellij.states.WokwiConfigState
import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.util.preferredWidth
import java.awt.Font
import javax.swing.*


class WokwiConfigPanelBuilder(val model: WokwiConfigState) {

    var onChangeAction: (() -> Unit)? = null

    fun build(): DialogPanel {
        var panel: DialogPanel? = null
        val action = ActionManager.getInstance().getAction("com.github.jozott00.wokwiintellij.actions.WokwiStartAction")

        fun onChange() {
            invokeLater {
                if (panel == null)
                    return@invokeLater

                panel!!.apply()
                onChangeAction?.invoke()
            }
        }

        panel = panel {
            lateinit var textField: Cell<TextFieldWithBrowseButton>

            row {
                button("Start Simulator", action)
                    .align(Align.CENTER)
                    .apply {
                        this.component.icon = AllIcons.Debugger.ThreadRunning
                    }
            }

            group("Simulation Settings") {
                row("ESP target device:") {
                    comboBox(ESPDevice.entries)
                        .onChanged { _ -> onChange() }
                        .bindItem(model::espDevice.toNullableProperty())
                }.rowComment("Wokwi simulator diagram is chosen based on target device.")

                row("Executable: ") {
                    textField = textFieldWithBrowseButton().apply {
                        component.preferredWidth = 300
                    }
                        .onChanged { _ -> onChange() }
                        .bindText(model::elfPath)
                }.rowComment("Path to ELF binary to run in simulator")

                row("Flash Size: ") {
                    comboBox(FlashSize.entries)
                        .onChanged { _ -> onChange() }
                        .bindItem(model::flashSize.toNullableProperty())
                }.rowComment("Must be compatible with device and partition table")
            }
        }
            .withVisualPadding()


        return panel
    }

}

fun wokwiConfigPanel(model: WokwiConfigState, build: WokwiConfigPanelBuilder.() -> Unit): DialogPanel {
    return WokwiConfigPanelBuilder(model).apply(build).build()
}

private fun JComponent.bold(isBold: Boolean) {
    font = font.deriveFont(if (isBold) Font.BOLD else Font.PLAIN)
}