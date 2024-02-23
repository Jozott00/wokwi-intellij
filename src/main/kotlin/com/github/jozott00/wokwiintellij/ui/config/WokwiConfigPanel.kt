package com.github.jozott00.wokwiintellij.ui.config

import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.util.preferredWidth
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo


class WokwiConfigPanelBuilder(val project: Project, private val model: WokwiSettingsState) {

    var onChangeAction: (() -> Unit)? = null

    fun build(): DialogPanel {
        var panel: DialogPanel? = null
        val action = ActionManager.getInstance().getAction("com.github.jozott00.wokwiintellij.actions.WokwiStartAction")

        fun onChange() {
            if (panel == null)
                return

            panel!!.apply()
            onChangeAction?.invoke()
        }

        panel = panel {
            row {
                button("Start Simulator", action)
                    .align(Align.CENTER)
                    .apply {
                        this.component.icon = AllIcons.Debugger.ThreadRunning
                    }
            }

            group("License") {
                row {
                    cell(LicensingPanel().component)
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

                row {
                    comment("The wokwi.toml holds all information the plugin needs to know. Visit <a href='https://docs.wokwi.com/vscode/project-config'>the wokwi.toml docs</a> for more information.")
                }
                    .bottomGap(BottomGap.SMALL)


                row("diagram.json path: ") {
                    textFieldWithBrowseButton { getRootRelativePathOf(it) }.apply {
                        component.preferredWidth = 400
                    }
                        .onChanged { _ -> onChange() }
                        .bindText(model::wokwiDiagramPath)
                }

                row {
                    comment("The diagram.json specifies the simulation runtime environment. Visit <a href='https://docs.wokwi.com/vscode/project-config'>the diagram.json docs</a> for more information.")
                }
            }
        }.apply {
            autoscrolls = true
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


}

fun wokwiConfigPanel(
    project: Project,
    model: WokwiSettingsState,
    build: WokwiConfigPanelBuilder.() -> Unit
): DialogPanel {
    return WokwiConfigPanelBuilder(project, model).apply(build).build()
}

