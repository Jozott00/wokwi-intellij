@file:Suppress("SameParameterValue")

package com.github.jozott00.wokwiintellij.ui.config

import com.github.jozott00.wokwiintellij.services.WokwiLicensingService
import com.github.jozott00.wokwiintellij.utils.runInBackground
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


class LicensingPanel : ComponentContainer {

    private val licensingService = ApplicationManager.getApplication().service<WokwiLicensingService>()

    private val licenseNotSetPanel: JPanel = buildStatus(false, "No license activated!")
    private val licenseInvalidPanel: JPanel = buildStatus(false, "License invalid!")
    private val licenseExpiredPanel: JPanel = buildStatus(false, "License expired!")
    private val licensePlanPanel = JLabel()
    private val licenseSetPanel: JPanel = buildStatus(true, "License set", licensePlanPanel)

    private val statusCardLayout = CardLayout()
    private val statusCard = JPanel(statusCardLayout).also {
        it.add("LOADING", AsyncProcessIcon("Loading"))
        it.add("LICENSE_MISSING", licenseNotSetPanel)
        it.add("LICENSE_INVALID", licenseInvalidPanel)
        it.add("LICENSE_EXPIRED", licenseExpiredPanel)
        it.add("LICENSE_SET", licenseSetPanel)
    }

    override fun getComponent() = panel {
        row {
            button("Activate License") {
                LicensingDialog().show()
                checkLicenseAvailability(true)
            }

//            button("Remove License") {
//                licensingService.removeLicense()
//                checkLicenseAvailability(true)
//            }
            cell(statusCard)

        }
        row {
            comment("Wokwi requires a license to run the simulator. All features supported by the plugin are community license features and therefore free.")
        }
    }.also {
        checkLicenseAvailability()
    }

    @Suppress("SameParameterValue")
    private fun checkLicenseAvailability(recentlyChanged: Boolean = false) = runInBackground {
        runBlocking(Dispatchers.IO) {
            if (recentlyChanged) {
                invokeLater { statusCardLayout.show(statusCard, "LOADING") }
                delay(500)
            }

            val raw = licensingService.getLicense()
                ?: return@runBlocking invokeLater {
                    statusCardLayout.show(statusCard, "LICENSE_MISSING")
                }

            invokeLater {
                val parsed = licensingService.parseLicense(raw)
                val statusPanel = when {
                    parsed == null -> "LICENSE_INVALID"
                    !parsed.isValid() -> "LICENSE_EXPIRED"
                    else -> {
                        licensePlanPanel.text = "(${parsed.plan ?: "Community"})"
                        "LICENSE_SET"
                    }
                }
                statusCardLayout.show(statusCard, statusPanel)
            }
        }
    }

    private fun buildStatus(valid: Boolean, message: String, plan: JLabel? = null) = panel {
        row {
            icon(if (valid) AllIcons.RunConfigurations.TestPassed else AllIcons.RunConfigurations.TestFailed)
                .gap(RightGap.SMALL)
            label(message)
                .gap(RightGap.SMALL)

            plan?.let {
                cell(plan)
            }
        }
    }

    override fun dispose() {

    }

    override fun getPreferredFocusableComponent(): JComponent {
        return component
    }

}


