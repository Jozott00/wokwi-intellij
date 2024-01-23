package com.github.jozott00.wokwiintellij.listeners

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

// TODO: Consider deletion
class WokwiPostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val projectService = project.service<WokwiProjectService>()

    }
}