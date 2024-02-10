package com.github.jozott00.wokwiintellij.toolWindow

import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.util.preferredWidth
import java.awt.Font
import javax.swing.*
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo


class WokwiConfigPanelBuilder(val project: Project, val model: WokwiSettingsState) {

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

            row {
                button("Start Simulator", action)
                    .align(Align.CENTER)
                    .apply {
                        this.component.icon = AllIcons.Debugger.ThreadRunning
                    }
            }

            group("Settings") {
                row("wokwi.toml path: ") {
                    textFieldWithBrowseButton { getRootRelativePathOf(it) }.apply {
                        component.preferredWidth = 400
                    }
                        .validationOnInput {
                            ValidationInfo("Hello world", it)
                        }
                        .validationOnApply {
                            this.error("Test error")
                        }
                        .onChanged {
                            onChange()
                        }
                        .bindText(model::wokwiConfigPath)

                }
                    .rowComment("The wokwi.toml holds all information the plugin needs to know. Visit <a href='https://docs.wokwi.com/vscode/project-config'>the wokwi.toml docs</a> for more information.")


                row("diagram.json path: ") {
                    textFieldWithBrowseButton { getRootRelativePathOf(it) }.apply {
                        component.preferredWidth = 400
                    }
                        .onChanged { _ -> onChange() }
                        .bindText(model::wokwiDiagramPath)
                }.rowComment("The diagram.json specifies the simulation runtime environment. Visit <a href='https://docs.wokwi.com/vscode/project-config'>the diagram.json docs</a> for more information.")
            }
        }
            .withVisualPadding()


        return panel
    }

    private fun getRootRelativePathOf(file: VirtualFile): String {
        val projectPath = project.guessProjectDir()?.toNioPath() ?: return file.path
        val resolved = projectPath.resolve(file.path)
        val relative = resolved.relativeTo(projectPath)
        return relative.pathString
    }


    companion object {
        val LOG = logger<WokwiConfigPanelBuilder>()
    }

}

fun wokwiConfigPanel(
    project: Project,
    model: WokwiSettingsState,
    build: WokwiConfigPanelBuilder.() -> Unit
): DialogPanel {
    return WokwiConfigPanelBuilder(project, model).apply(build).build()
}

private fun JComponent.bold(isBold: Boolean) {
    font = font.deriveFont(if (isBold) Font.BOLD else Font.PLAIN)
}