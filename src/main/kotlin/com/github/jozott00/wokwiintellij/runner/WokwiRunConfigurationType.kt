// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.github.jozott00.wokwiintellij.runner

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.util.NotNullLazyValue

class WokwiRunConfigurationType : ConfigurationTypeBase(
    ID, "Wokwi", "Demo run configuration type",
    NotNullLazyValue.createValue { WokwiIcons.ConsoleToolWindowIcon }) {
    init {
        addFactory(WokwiConfigurationFactory(this))
    }

    companion object {
        const val ID: String = WokwiConstants.WOKWI_RUN_CONFIG_ID
    }
}