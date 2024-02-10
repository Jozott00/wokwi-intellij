package com.github.jozott00.wokwiintellij.ui.config

import com.intellij.openapi.ui.ComponentContainer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent


class LicensingPanel : ComponentContainer {
    override fun getComponent() = panel {
        row {
            button("Activate License") {
                LicensingDialog().show()
            }
                .align(Align.FILL)

            comment("Wokwi requires a license to run the simulator. All features supported by the plugin are community license features and therefore free.")
        }
    }

    override fun dispose() {

    }

    override fun getPreferredFocusableComponent(): JComponent {
        return component
    }

}


