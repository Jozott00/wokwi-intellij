// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.github.jozott00.wokwiintellij.runner

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class WokwiSettingsEditor : SettingsEditor<WokwiRunConfiguration>() {
    private val myPanel: JPanel
    private val scriptPathField = TextFieldWithBrowseButton()

    init {
        scriptPathField.addBrowseFolderListener(
            "Select Script File", null, null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Script file", scriptPathField)
            .panel
    }

    override fun resetEditorFrom(demoRunConfiguration: WokwiRunConfiguration) {
        scriptPathField.setText(demoRunConfiguration.scriptName)
    }

    override fun applyEditorTo(demoRunConfiguration: WokwiRunConfiguration) {
        demoRunConfiguration.scriptName = scriptPathField.text
    }

    override fun createEditor(): JComponent {
        return myPanel
    }
}