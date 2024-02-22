package com.github.jozott00.wokwiintellij.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * The service is intended to be used instead of a project as a parent disposable.
 */
@Service(Service.Level.PROJECT)
class WokwiPluginDisposable: Disposable {
    companion object {
        fun getInstance(project: Project) = project.service<WokwiPluginDisposable>()
    }

    override fun dispose() {  }
}