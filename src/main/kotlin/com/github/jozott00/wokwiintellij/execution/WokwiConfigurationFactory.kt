package com.github.jozott00.wokwiintellij.execution

import com.github.jozott00.wokwiintellij.execution.configs.*
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.project.Project

class WokwiConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String {
        return type.id
    }

    override fun createTemplateConfiguration(
        project: Project
    ) = when (type) {
        is WokwiStartDebugConfigType -> WokwiStartDebugConfig(project, this, type.displayName)
        is WokwiRunConfigType -> WokwiRunConfig(project, this, type.displayName)
        else -> error("Invalid configuration type")
    }


    override fun getOptionsClass() = when (type) {
        is WokwiStartDebugConfigType -> WokwiStartDebugConfigOptions::class.java
        is WokwiRunConfigType -> WokwiRunConfigOptions::class.java
        else -> error("Invalid configuration type")
    }
}