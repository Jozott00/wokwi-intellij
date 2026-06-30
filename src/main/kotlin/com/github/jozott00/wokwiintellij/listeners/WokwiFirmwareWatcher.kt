package com.github.jozott00.wokwiintellij.listeners

import com.github.jozott00.wokwiintellij.ide.simulator.WokwiSessionController
import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.Path

class WokwiFirmwareWatcher(val project: Project) : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        if (project.isDisposed || !project.isInitialized) return

        val configState = project.service<WokwiSettingsState>()
        val projectService = project.service<WokwiSessionController>()

        if (!configState.watchFirmware) return
        val watchPaths = projectService.getWatchPaths() ?: return


        val result = events.find {
            if (it.file?.isInLocalFileSystem != true)
                return@find false

            val eventPath = it.file?.path?.let { path -> Path.of(path).normalize() } ?: return@find false

            if (watchPaths.contains(eventPath))
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
