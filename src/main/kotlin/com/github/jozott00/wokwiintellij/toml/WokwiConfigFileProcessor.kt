package com.github.jozott00.wokwiintellij.toml

import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.toml.lang.psi.TomlFile

object WokwiConfigFileProcessor {
    fun processConfigFile(project: Project, filePath: String): WokwiConfig? {
        val projectDir = project.guessProjectDir() ?: return null
        val confFile = projectDir.findFileByRelativePath(filePath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(confFile) ?: return null


        val tomlFile = psiFile as? TomlFile ?: run {
            notifyError("Configuration must be a TOML file")
            return null
        }

        val wokwiTable = tomlFile.findTable("wokwi") ?: run {
            notifyError("No [wokwi] table found")
            return null
        }





        return null
    }

    private fun notifyError(error: String) {
        WokwiNotifier.notifyBalloon(
            "Couldn't load Wokwi configuration",
            error,
            NotificationType.ERROR
        )
    }


}