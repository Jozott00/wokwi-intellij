package com.github.jozott00.wokwiintellij.listeners

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.states.WokwiConfigState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class WokwiElfFileListener(val project: Project) : BulkFileListener {


    override fun after(events: MutableList<out VFileEvent>) {
        val configState = project.service<WokwiConfigState>()

        if (!configState.watchElf) return

        val watchPath = configState.elfPath

        val result = events.find {
            if (it.file?.isInLocalFileSystem != true)
                return@find false

            if (it.file?.path == watchPath)
                return@find true

            false
        }

        val projectService = project.service<WokwiProjectService>()
        if (result != null) {
            projectService.elfFileUpdate()
        }
    }

}