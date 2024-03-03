package com.github.jozott00.wokwiintellij.toml

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import com.akuleshov7.ktoml.file.TomlFileReader
import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.extensions.findRelativeFiles
import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.utils.NotifyAction
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
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
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer

object WokwiConfigProcessor {

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
        return readConfig(configFile, project)
    }

    suspend fun findElfFile(project: Project): VirtualFile? {
        val projectSettings = project.service<WokwiSettingsState>()
        val configFile = findWokwiConfigPath(projectSettings.wokwiConfigPath, project) ?: return null
        val tomlConfig = readConfig(project) ?: return null
        return configFile.parent.findFileByRelativePath(tomlConfig.elf)
    }

    private suspend fun readConfig(configFile: VirtualFile, project: Project): WokwiTomlTable? {

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
            model = fileReader.decodeFromFile(serializer(), configFile.path)
        } catch (e: TomlDecodingException) {
            notifyError(
                "Check your wokwi.toml file and try again",
                getNotifyJumpToAction("Jump to config", project, configFile)
            )
            return null
        }

        return model.wokwi
    }

    private suspend fun loadConfig(
        project: Project,
        tomlConfig: WokwiTomlTable,
        configFile: VirtualFile,
        diagramFile: VirtualFile
    ): WokwiConfig? {
        val configDir = readAction { configFile.parent }

        val elfFile = readAction { configDir.findFileByRelativePath(tomlConfig.elf) } ?: run {
            notifyError(
                "Invalid ELF path. Is the project already built?",
                getNotifyJumpToAction("Jump to config", project, configFile)
            )
            return null
        }

        val firmwareFile = readAction { configDir.findFileByRelativePath(tomlConfig.firmware) } ?: run {
            notifyError(
                "Invalid firmware path. Is the project already built?",
                getNotifyJumpToAction("Jump to config", project, configFile)
            )
            return null
        }


        return WokwiConfig(
            version = tomlConfig.version.toString(),
            elf = elfFile,
            firmware = firmwareFile,
            diagram = diagramFile,
            gdbServerPort = tomlConfig.gdbServerPort
        )
    }


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

    private suspend fun findWokwiConfigPath(wokwiConfigPath: String, project: Project): VirtualFile? = withContext(Dispatchers.IO) { readAction { project
        .findRelativeFiles(wokwiConfigPath)}.run {
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
    }}

    private suspend fun findWokwiDiagramPath(wokwiDiagramPath: String, project: Project): VirtualFile? = withContext(Dispatchers.IO) {readAction {  project
        .findRelativeFiles(wokwiDiagramPath) }.run {
        if (isEmpty()) {
            notifyError(
                "Diagram file `$wokwiDiagramPath` not found in project.",
                NotifyAction("Create diagram.json") { _, _ ->
                    val psiManager = PsiManager.getInstance(project)
                    val virtualFile = project.guessProjectDir() ?: return@NotifyAction
                    val psiDir = psiManager.findDirectory(virtualFile)
                    WriteCommandAction.runWriteCommandAction(project) {
                        val diagramFile =
                            psiDir?.createFile(WokwiConstants.WOKWI_DIAGRAM_FILE) ?: return@runWriteCommandAction
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
    }}


}