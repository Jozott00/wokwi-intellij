package com.github.jozott00.wokwiintellij.runner.configs

import com.github.jozott00.wokwiintellij.runner.WokwiConfigurationFactory
import com.github.jozott00.wokwiintellij.runner.profileStates.WokwiSimulatorRunnerState
import com.github.jozott00.wokwiintellij.runner.profileStates.WokwiSimulatorStartState
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import javax.swing.JComponent
import javax.swing.JPanel

class WokwiRunConfig(
    project: Project,
    factory: ConfigurationFactory, name: String
) : RunConfigurationBase<WokwiRunConfigOptions>(project, factory, name) {

    override fun getOptions(): WokwiRunConfigOptions {
        return super.getOptions() as WokwiRunConfigOptions
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment) =
        WokwiSimulatorRunnerState(environment)

    override fun getConfigurationEditor() = WokwiRunEditor()

}

class WokwiRunConfigType : ConfigurationTypeBase(
    ID, "Wokwi Run", "Run the Wokwi simulator and let it wait for a GDB debugger.",
    NotNullLazyValue.createValue { WokwiIcons.Debug }
) {
    init {
        addFactory(WokwiConfigurationFactory(this))
    }

    companion object {
        const val ID: String = "WowkiRunConfig"
    }
}


class WokwiRunConfigOptions : RunConfigurationOptions() {

}


class WokwiRunEditor : SettingsEditor<WokwiRunConfig>() {
    override fun resetEditorFrom(s: WokwiRunConfig) {
        // currently no settings
    }

    override fun applyEditorTo(s: WokwiRunConfig) {
        // currently no settings
    }

    override fun createEditor(): JComponent {
        // currently no settings
        return JPanel()
    }

}