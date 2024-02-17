package com.github.jozott00.wokwiintellij.runner.configs

import com.github.jozott00.wokwiintellij.runner.WokwiConfigurationFactory
import com.github.jozott00.wokwiintellij.runner.profileStates.WokwiSimulatorStartState
import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import javax.swing.JComponent
import javax.swing.JPanel

class WokwiStartDebugConfig(
    project: Project,
    factory: ConfigurationFactory, name: String
) : RunConfigurationBase<WokwiStartDebugConfigOptions>(project, factory, name) {

    override fun getOptions(): WokwiStartDebugConfigOptions {
        return super.getOptions() as WokwiStartDebugConfigOptions
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment) =
        WokwiSimulatorStartState(project, true)
//    override fun getState(executor: Executor, environment: ExecutionEnvironment) =
//        WokwiSimulatorRunnerState(environment)

    override fun getConfigurationEditor() = WokwiStartDebugEditor()

}

class WokwiStartDebugConfigType : ConfigurationTypeBase(
    ID, "Start Debug", "Start the Wokwi simulator and let it wait for a GDB debugger.",
    NotNullLazyValue.createValue { WokwiIcons.Debug }
) {
    init {
        addFactory(WokwiConfigurationFactory(this))
    }

    companion object {
        const val ID: String = "WowkiStartDebugConfig"
    }
}


class WokwiStartDebugConfigOptions : RunConfigurationOptions() {

}


class WokwiStartDebugEditor : SettingsEditor<WokwiStartDebugConfig>() {
    override fun resetEditorFrom(s: WokwiStartDebugConfig) {
        // currently no settings
    }

    override fun applyEditorTo(s: WokwiStartDebugConfig) {
        // currently no settings
    }

    override fun createEditor(): JComponent {
        // currently no settings
        return JPanel()
    }

}