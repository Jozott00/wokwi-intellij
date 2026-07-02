package com.github.jozott00.wokwiintellij.ide.config

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.core.config.WokwiConfigResolveError
import com.github.jozott00.wokwiintellij.core.config.WokwiConfigResolveResult
import com.github.jozott00.wokwiintellij.core.config.WokwiConfigResolver
import com.github.jozott00.wokwiintellij.core.config.WokwiResolvedCustomChip
import com.github.jozott00.wokwiintellij.core.config.WokwiTomlConfig
import com.github.jozott00.wokwiintellij.core.config.WokwiTomlParseResult
import com.github.jozott00.wokwiintellij.core.config.WokwiTomlParser
import com.github.jozott00.wokwiintellij.core.config.WokwiTomlTable
import com.github.jozott00.wokwiintellij.core.ports.ProjectFiles
import com.github.jozott00.wokwiintellij.extensions.findRelativeFiles
import com.github.jozott00.wokwiintellij.ide.services.IntelliJProjectFiles
import com.github.jozott00.wokwiintellij.ide.services.IntelliJUserNotifier
import com.github.jozott00.wokwiintellij.services.UserNotificationAction
import com.github.jozott00.wokwiintellij.services.UserNotifier
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.utils.WokwiTemplates
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Project paths and startup-only values resolved from `wokwi.toml` and plugin settings.
 *
 * This object is an IntelliJ-side intermediate result. It deliberately does not live in `core` and is not passed to the
 * browser/session layer.
 */
data class ResolvedWokwiProjectConfig(
    val firmwarePath: Path,
    val diagramPath: Path,
    val gdbServerPort: Int?,
    val customChips: List<WokwiResolvedCustomChip> = emptyList(),
)

/**
 * Resolves Wokwi project configuration from IntelliJ project state.
 *
 * This IntelliJ adapter owns project file discovery, editor navigation, and user-facing error reporting. Pure TOML
 * parsing and path resolution live in `core/config`.
 */
class WokwiProjectConfigResolver(
    private val project: Project,
    private val projectFiles: ProjectFiles = IntelliJProjectFiles,
    private val userNotifier: UserNotifier = IntelliJUserNotifier,
) {

    /**
     * Resolves the configured Wokwi files into normalized local paths.
     *
     * The ELF path is validated even though it is not returned, because an invalid ELF entry usually means the project
     * has not been built and the simulator should not start.
     *
     * @param wokwiConfigPath project-relative path or search pattern for `wokwi.toml`.
     * @param diagramPath project-relative path or search pattern for `diagram.json`.
     * @return resolved project configuration, or `null` after reporting a user-facing error.
     */
    suspend fun resolve(wokwiConfigPath: String, diagramPath: String): ResolvedWokwiProjectConfig? {
        val absoluteWokwiPath = findWokwiConfigPath(wokwiConfigPath) ?: return null
        val diagramFilePath = findWokwiDiagramPath(diagramPath) ?: return null
        val tomlConfig = withContext(Dispatchers.IO) {
            readTomlConfig(absoluteWokwiPath)
        } ?: return null
        return withContext(Dispatchers.IO) {
            resolveConfig(tomlConfig, absoluteWokwiPath, diagramFilePath)
        }
    }

    /**
     * Reads the configured `wokwi.toml` file and returns its `[wokwi]` table.
     *
     * This is used by IDE features that need raw TOML settings without loading the full simulator runtime payload.
     */
    suspend fun readConfig(): WokwiTomlTable? {
        val projectSettings = project.service<WokwiSettingsState>()
        val configFile = findWokwiConfigPath(projectSettings.wokwiConfigPath) ?: return null
        return readTomlConfig(configFile)?.wokwi
    }

    /**
     * Resolves the ELF file referenced by the configured `wokwi.toml`.
     *
     * The debugger run-configuration macro uses this to expand the ELF path without starting the simulator.
     */
    suspend fun findElfFile(): VirtualFile? {
        val projectSettings = project.service<WokwiSettingsState>()
        val configFile = findWokwiConfigPath(projectSettings.wokwiConfigPath) ?: return null
        val tomlConfig = readTomlConfig(configFile) ?: return null
        return configFile.parent.findFileByRelativePath(tomlConfig.wokwi.elf)
    }

    private suspend fun readTomlConfig(configFile: VirtualFile): WokwiTomlConfig? {
        if (!configFile.exists()) {
            notifyError("Configuration file `${configFile.path}` not found.")
            return null
        }

        if (configFile.name != WokwiConstants.WOKWI_CONFIG_FILE) {
            notifyError("Wokwi configuration file must be called `wokwi.toml` but is actually `${configFile.name}`")
            return null
        }

        return when (val result = WokwiTomlParser.parse(projectFiles.readString(Path.of(configFile.path)))) {
            is WokwiTomlParseResult.Failure -> {
                notifyError(
                    "Check your wokwi.toml file and try again. Full error message: ${result.message}",
                    getNotifyJumpToAction("Jump to config", configFile)
                )
                null
            }

            is WokwiTomlParseResult.Success -> result.config
        }
    }

    private suspend fun resolveConfig(
        tomlConfig: WokwiTomlConfig,
        configFile: VirtualFile,
        diagramFile: VirtualFile
    ): ResolvedWokwiProjectConfig? {
        return when (
            val result = WokwiConfigResolver.resolve(
                configFile = Path.of(configFile.path),
                diagramFile = Path.of(diagramFile.path),
                config = tomlConfig,
                projectFiles = projectFiles,
            )
        ) {
            is WokwiConfigResolveResult.Success -> ResolvedWokwiProjectConfig(
                firmwarePath = result.config.firmwarePath,
                diagramPath = result.config.diagramPath,
                gdbServerPort = result.config.gdbServerPort,
                customChips = result.config.customChips,
            )

            is WokwiConfigResolveResult.Failure -> {
                notifyError(resolveErrorMessage(result.error), getNotifyJumpToAction("Jump to config", configFile))
                null
            }
        }
    }

    private fun notifyError(error: String, action: UserNotificationAction? = null) {
        userNotifier.error(
            "Couldn't load Wokwi configuration",
            error,
            action,
        )
    }

    private fun resolveErrorMessage(error: WokwiConfigResolveError): String =
        when (error) {
            WokwiConfigResolveError.InvalidElfPath -> "Invalid ELF path. Is the project already built?"
            WokwiConfigResolveError.InvalidFirmwarePath -> "Invalid firmware path. Is the project already built?"
            WokwiConfigResolveError.InvalidDiagramPath -> "Invalid diagram path."
            WokwiConfigResolveError.InvalidCustomChipBinaryPath -> "Invalid custom chip WASM path."
            WokwiConfigResolveError.InvalidCustomChipJsonPath -> "Invalid custom chip JSON path."
        }

    @Suppress("SameParameterValue")
    private fun getNotifyJumpToAction(text: String, file: VirtualFile) = UserNotificationAction(text) {
        val descriptor = OpenFileDescriptor(project, file)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    private suspend fun findWokwiConfigPath(wokwiConfigPath: String): VirtualFile? =
        withContext(Dispatchers.IO) {
            readAction {
                project
                    .findRelativeFiles(wokwiConfigPath)
            }
        }.run {
            if (isEmpty()) {
                userNotifier.error(
                    "Failed to load Wokwi config",
                    "Configuration file `$wokwiConfigPath` not found in project.",
                )
                return@run null
            }
            if (size > 1) {
                notifyError("Found multiple configuration files: \n${joinToString("\n")}. \nSpecify the concrete one in the Settings.")
                return@run null
            }

            return@run first()
        }

    private suspend fun findWokwiDiagramPath(wokwiDiagramPath: String): VirtualFile? =
        withContext(Dispatchers.IO) {
            readAction {
                project
                    .findRelativeFiles(wokwiDiagramPath)
            }.run {
                if (isEmpty()) {
                    notifyError(
                        "Diagram file `$wokwiDiagramPath` not found in project.",
                        UserNotificationAction("Create diagram.json") {
                            val psiManager = PsiManager.getInstance(project)
                            val virtualFile = project.guessProjectDir() ?: return@UserNotificationAction
                            val psiDir = psiManager.findDirectory(virtualFile)
                            WriteCommandAction.runWriteCommandAction(project) {
                                val diagramFile =
                                    psiDir?.createFile(WokwiConstants.WOKWI_DIAGRAM_FILE)
                                        ?: return@runWriteCommandAction
                                val document = diagramFile.viewProvider.document
                                document.setText(WokwiTemplates.defaultDiagramJson())
                                val descriptor =
                                    OpenFileDescriptor(project, diagramFile.virtualFile)
                                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                            }
                        }
                    )
                    return@run null
                }
                if (size > 1) {
                    notifyError("Found multiple diagram files: \n${joinToString("\n")}. \nSpecify the concrete one in the Settings.")
                    return@run null
                }
                return@run first()
            }
        }
}
