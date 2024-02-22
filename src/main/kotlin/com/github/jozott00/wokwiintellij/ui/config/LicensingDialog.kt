package com.github.jozott00.wokwiintellij.ui.config

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.services.WokwiLicensingService
import com.github.jozott00.wokwiintellij.utils.runInBackground
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*

class LicensingDialog : DialogWrapper(true) {

    private var licenseKeyArea: JBTextArea? = null
    private val licensingService = ApplicationManager.getApplication().service<WokwiLicensingService>()

    init {
        title = "Activate Wokwi License"

        init()
    }

    override fun createCenterPanel() = panel {
        row {
            label("Get your license from wokwi.com and paste it below to use the Wokwi simulator.")
        }
        row {
            button("Open wokwi.com") {
                BrowserUtil.browse("https://wokwi.com/license?v=${WokwiConstants.WOKWI_WCODE_VERSION}")
            }
        }
        separator()
        row {
            label("Enter license key:")
        }
        row {
            licenseKeyArea = textArea()
                .apply {
                    component.lineWrap = true
                    component.wrapStyleWord = true
                }
                .onChanged {
                    initValidation()
                }
                .rows(5)
                .align(Align.FILL)
                .component
        }
    }


    override fun doValidate(): ValidationInfo? {
        val license = licenseKeyArea?.text
        if (license.isNullOrEmpty()) {
            return ValidationInfo("License key cannot be empty")
        }
        val wokwiLicense = licensingService.parseLicense(license)
            ?: return ValidationInfo("Invalid license key")


        if (wokwiLicense.expiration < Date()) {
            return ValidationInfo("License has expired")
        }

        return null
    }

    override fun doOKAction() {
        super.doOKAction()
        licenseKeyArea?.text?.let { licensingService.updateLicense(it) }
    }
}