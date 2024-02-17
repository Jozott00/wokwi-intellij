package com.github.jozott00.wokwiintellij.toml

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import com.akuleshov7.ktoml.file.TomlFileReader
import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.simulator.WokwiConfig
import com.github.jozott00.wokwiintellij.utils.NotifyAction
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.github.jozott00.wokwiintellij.utils.WokwiTemplates
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import kotlinx.serialization.serializer
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

object WokwiConfigProcessor {

    fun loadConfig(project: Project, wokwiConfigPath: String, diagramPath: String): WokwiConfig? {
        val projectPath = project.guessProjectDir()?.path ?: return null
        val tomlConfig = readConfig(project, wokwiConfigPath) ?: return null
        val configFilePath = Paths.get(projectPath).resolve(wokwiConfigPath)
        val diagramFilePath = Paths.get(projectPath).resolve(diagramPath)
        return loadConfig(project, tomlConfig, configFilePath, diagramFilePath)
    }

    private fun readConfig(project: Project, wokwiConfigPath: String): WokwiTomlTable? {
        val projectPath = project.guessProjectDir()?.path ?: return null
        val configFilePath = Paths.get(projectPath).resolve(wokwiConfigPath)

        if (!configFilePath.toFile().exists()) {
            notifyError("Configuration file `${configFilePath.pathString}` not found.")
            return null
        }

        if (configFilePath.fileName.pathString != "wokwi.toml") {
            notifyError("Wokwi configuration file must be called `wokwi.toml` but is actually `${configFilePath.fileName}`")
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
            model = fileReader.decodeFromFile(serializer(), configFilePath.pathString)
        } catch (e: TomlDecodingException) {
            notifyError(
                "Check your wokwi.toml file and try again",
                getNotifyJumpToAction("Jump to config", project, configFilePath)
            )
            return null
        }

        return model.wokwi
    }

    private fun loadConfig(
        project: Project,
        tomlConfig: WokwiTomlTable,
        configFilePath: Path,
        diagramFilePath: Path
    ): WokwiConfig? {

        val projectDir = project.guessProjectDir() ?: return null;


        val elfFile = projectDir.findFileByRelativePath(tomlConfig.elf) ?: run {
            notifyError(
                "Invalid ELF path. Is the project already built?",
                getNotifyJumpToAction("Jump to config", project, configFilePath)
            )
            return null
        }

        val firmwareFile = projectDir.findFileByRelativePath(tomlConfig.elf) ?: run {
            notifyError(
                "Invalid firmware path. Is the project already built?",
                getNotifyJumpToAction("Jump to config", project, configFilePath)
            )
            return null
        }

        val diagramFile = LocalFileSystem.getInstance().findFileByPath(diagramFilePath.toString()) ?: run {
            notifyError(
                "Diagram specification `${diagramFilePath.pathString}` not found",
                NotifyAction("Create diagram.json") { _, _ ->
                    val psiManager = PsiManager.getInstance(project)
                    val virtualFile = project.guessProjectDir() ?: return@NotifyAction
                    val psiDir = psiManager.findDirectory(virtualFile)
                    WriteCommandAction.runWriteCommandAction(project) {
                        val diagramFile =
                            psiDir?.createFile(WokwiConstants.WOKWI_DIAGRAM_FILE) ?: return@runWriteCommandAction
                        val document = diagramFile.viewProvider.document
                        document.setText(WokwiTemplates.defaultDiagramJson(project))
                        val descriptor = OpenFileDescriptor(project, diagramFile.virtualFile)
                        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                    }
                }
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

    private fun notifyError(error: String, action: NotifyAction? = null) {
        WokwiNotifier.notifyBalloon(
            "Couldn't load Wokwi configuration",
            error,
            NotificationType.ERROR,
            action
        )
    }

    private fun getNotifyJumpToAction(text: String, project: Project, filePath: Path) = NotifyAction(text) { _, _ ->
        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(filePath) ?: return@NotifyAction
        val descriptor = OpenFileDescriptor(project, virtualFile)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }


}