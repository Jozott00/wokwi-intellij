package com.github.jozott00.wokwiintellij.listeners

import com.github.jozott00.wokwiintellij.services.WokwiProjectService
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class WokwiFirmwareWatcher(val project: Project) : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        val configState = project.service<WokwiSettingsState>()
        val projectService = project.service<WokwiProjectService>()

        if (!configState.watchFirmware) return
        val watchPaths = projectService.getWatchPaths() ?: return


        val result = events.find {
            if (it.file?.isInLocalFileSystem != true)
                return@find false

            if (watchPaths.contains(it.file?.path))
                return@find true

            false
        }

        if (result != null) {
            LOG.info("Triggered with: ${events.map { it.path }}")
            LOG.info("Watch against: $watchPaths")
            projectService.firmwareUpdated()
        }
    }

    companion object {
        private val LOG = logger<WokwiFirmwareWatcher>()
    }

}