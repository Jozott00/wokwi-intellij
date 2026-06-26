package com.github.jozott00.wokwiintellij.toml

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.extensions.findRelativeFiles
import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.simulator.WokwiCustomChip
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.utils.NotifyAction
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier.notifyBalloonAsync
import com.github.jozott00.wokwiintellij.utils.WokwiTemplates
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readBytes
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/**
 * Simulation configurations loader/handler object.
 */
object WokwiConfigProcessor {
    /**
     * (METHOD) Reads and loads configurations and setup data for the new simulation.
     * @param project OpenAPI project object.
     * @param wokwiConfigPath wokwi.toml configuration file path.
     * @param diagramPath diagram.json scene description file path.
     * @return WokwiConfig? object with all configurations ready for simulation init.
     */
    suspend fun loadConfig(project: Project, wokwiConfigPath: String, diagramPath: String): WokwiConfig? {
        val absoluteWokwiPath = findWokwiConfigPath(wokwiConfigPath, project) ?: return null
        val diagramFilePath = findWokwiDiagramPath(diagramPath, project) ?: return null
        val tomlConfig = withContext(Dispatchers.IO) {
            readConfig(absoluteWokwiPath, project)
        } ?: return null
        return withContext(Dispatchers.IO) {
            loadConfig(project, tomlConfig, absoluteWokwiPath, diagramFilePath)
        }
    }

    suspend fun readConfig(project: Project): WokwiTomlTable? {
        val projectSettings = project.service<WokwiSettingsState>()
        val configFile = findWokwiConfigPath(projectSettings.wokwiConfigPath, project) ?: return null
        return readConfig(configFile, project)?.wokwi
    }

    suspend fun findElfFile(project: Project): VirtualFile? {
        val projectSettings = project.service<WokwiSettingsState>()
        val configFile = findWokwiConfigPath(projectSettings.wokwiConfigPath, project) ?: return null
        val tomlConfig = readConfig(project) ?: return null
        return configFile.parent.findFileByRelativePath(tomlConfig.elf)
    }

    /**
     * (METHOD) Reads and fetches simulation configurations from wokwi.toml.
     * @param configFile wokwi.toml IO object (VirtualFile).
     * @param project OpenAPI Project object.
     * @return A WokwiTomlConfig? object with the retrieved configurations.
     */
    private suspend fun readConfig(configFile: VirtualFile, project: Project): WokwiTomlConfig? {

        if (!configFile.exists()) {
            notifyError("Configuration file `${configFile.path}` not found.")
            return null
        }

        if (configFile.name != "wokwi.toml") {
            notifyError("Wokwi configuration file must be called `wokwi.toml` but is actually `${configFile.name}`")
            return null
        }

        val fileReader = TomlFileReader(
            inputConfig = TomlInputConfig(
                ignoreUnknownNames = true,
                allowNullValues = true
            )
        )
        lateinit var model: WokwiTomlConfig
        try {
            model = fileReader.decodeFromFile(serializer(), configFile.path);
        } catch (e: Exception) {
            notifyError(
                "Check your wokwi.toml file and try again. Full error message: ${e.message}",
                getNotifyJumpToAction("Jump to config", project, configFile)
            )
            return null;
        }

        return model;
    }

    /**
     * (METHOD) Loads and interprets setup files linked by the configuration file onto the new simulation.
     * @param project Project object.
     * @param tomlConfig Data from the wokwi toml table structure (from wokwi.toml).
     * @param configFile wokwi.toml configuration VirtualFile.
     * @param diagramFile diagram.json scene design VirtualFile.
     * @return A WokwiConfig? object containing the structured retrieved information.
     */
    private suspend fun loadConfig(
        project: Project,
        tomlConfig: WokwiTomlConfig,
        configFile: VirtualFile,
        diagramFile: VirtualFile
    ): WokwiConfig? {
        val configDir = readAction { configFile.parent }

        val elfFile = readAction { configDir.findFileByRelativePath(tomlConfig.wokwi.elf) } ?: run {
            notifyError(
                "Invalid ELF path. Is the project already built?",
                getNotifyJumpToAction("Jump to config", project, configFile)
            )
            return null
        }

        val firmwareFile = readAction { configDir.findFileByRelativePath(tomlConfig.wokwi.firmware) } ?: run {
            notifyError(
                "Invalid firmware path. Is the project already built?",
                getNotifyJumpToAction("Jump to config", project, configFile)
            )
            return null
        }

        val chips = mutableListOf<WokwiCustomChip>();

        for (chipConfig in tomlConfig.chip) {
            val chipWasmFile = readAction { configDir.findFileByRelativePath(chipConfig.binary) } ?: run {
                notifyError(
                    "Invalid wasm path.",
                    getNotifyJumpToAction("Jump to config", project, configFile)
                )
                return null
            }

            val chipBinaryBuffer = loadChipBIN(chipWasmFile) ?: return null

            val chipJsonPath = chipConfig.binary.removeSuffix(".wasm") + ".json"
            val chipJSONFile = readAction { configDir.findFileByRelativePath(chipJsonPath) } ?: run {
                notifyError(
                    "Invalid json path.",
                    getNotifyJumpToAction("Jump to config", project, configFile)
                )
                return null
            }

            val chipJSONContent = readAction { chipJSONFile.readText() }
            val chipJson = try {
                Json.parseToJsonElement(chipJSONContent)
            } catch (e: Exception) {
                notifyError(
                    "Invalid chip JSON `${chipJSONFile.path}`. Full error message: ${e.message}",
                    getNotifyJumpToAction("Jump to config", project, chipJSONFile)
                )
                return null
            }

            chips.add(WokwiCustomChip (
                name =  chipConfig.name,
                binaryBase64 = encodeBase64(chipBinaryBuffer),
                json = chipJson
            ));
        }

        return WokwiConfig(
            version = tomlConfig.wokwi.version.toString(),
            elf = elfFile,
            firmware = firmwareFile,
            diagram = diagramFile,
            gdbServerPort = tomlConfig.wokwi.gdbServerPort,
            chips = chips,
        )
    }

    /**
     * (METHOD) Loads custom chip binary data from the chip.wasm file.
     * @param chipBinFile WebAssembly-compiled chip source-code VirtualFile.
     * @return The chip's ByteArray? binary data buffer. Returns null if the file is not found.
     */
    suspend fun loadChipBIN(chipBinFile : VirtualFile): ByteArray? = withContext(Dispatchers.IO) {
        if (!readAction { chipBinFile.exists() }) {
            withContext(Dispatchers.EDT) {
                notifyBalloonAsync(
                    title = "Failed to load firmware",
                    message = "File `${chipBinFile.path}` does not exist and therefore cannot be loaded for simulation.",
                    NotificationType.ERROR
                )
            }
            return@withContext null
        }

        val buffer = readAction { chipBinFile.readBytes() }
        if (buffer.isEmpty()) {
            withContext(Dispatchers.EDT) {
                notifyBalloonAsync(
                    title = "Failed to load custom chip",
                    message = "File `${chipBinFile.path}` is empty and therefore cannot be loaded for simulation.",
                    NotificationType.ERROR
                )
            }
            return@withContext null
        }

        buffer
    }

    /**
     * (METHOD) Converts a byteArray into a Base64 binary string.
     * @param buffer The ByteArray buffer object.
     * @return Base64-encoded binary string.
     */
    private fun encodeBase64(buffer: ByteArray): String = Base64.encode(buffer)

    /**
     * (METHOD) Displays a visual error notification in the editor's bottom left corner, when Wokwi configurations fail to be loaded.
     * @param error Error message string to be displayed.
     * @param action Custom action button inside the notification dialog, associated with a given procedure (function). Defaults to null.
     */
    private suspend fun notifyError(error: String, action: NotifyAction? = null) {
        withContext(Dispatchers.EDT) {
            WokwiNotifier.notifyBalloonAsync(
                "Couldn't load Wokwi configuration",
                error,
                NotificationType.ERROR,
                action
            )
        }
    }

    @Suppress("SameParameterValue")
    private fun getNotifyJumpToAction(text: String, project: Project, file: VirtualFile) = NotifyAction(text) { _, _ ->
        val descriptor = OpenFileDescriptor(project, file)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    private suspend fun findWokwiConfigPath(wokwiConfigPath: String, project: Project): VirtualFile? =
        withContext(Dispatchers.IO) {
            readAction {
                project
                    .findRelativeFiles(wokwiConfigPath)
            }
        }.run {
            if (isEmpty()) {
                WokwiNotifier.notifyBalloon(
                    "Failed to load Wokwi config",
                    "Configuration file `$wokwiConfigPath` not found in project.",
                    type = NotificationType.ERROR
                )
                return@run null
            }
            if (size > 1) {
                notifyError("Found multiple configuration files: \n${joinToString("\n")}. \nSpecify the concrete one in the Settings.")
                return@run null
            }

            return@run first()
        }

    private suspend fun findWokwiDiagramPath(wokwiDiagramPath: String, project: Project): VirtualFile? =
        withContext(Dispatchers.IO) {
            readAction {
                project
                    .findRelativeFiles(wokwiDiagramPath)
            }.run {
                if (isEmpty()) {
                    notifyError(
                        "Diagram file `$wokwiDiagramPath` not found in project.",
                        NotifyAction("Create diagram.json") { _, _ ->
                            val psiManager = PsiManager.getInstance(project)
                            val virtualFile = project.guessProjectDir() ?: return@NotifyAction
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