package com.github.jozott00.wokwiintellij.runner

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project

class WokwiConfigurationFactory(type: ConfigurationType?) : ConfigurationFactory(type!!) {
    override fun getId(): String {
        return WokwiRunConfigurationType.ID
    }

    override fun createTemplateConfiguration(
        project: Project
    ): RunConfiguration {
        val conf = WokwiRunConfiguration(project, this, "Start Wokwi")
        return conf
    }

    override fun getOptionsClass(): Class<out BaseState?> {
        return WokwiRunConfigurationOptions::class.java
    }
}
