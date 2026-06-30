package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.extensions.wokwiDisposable
import com.github.jozott00.wokwiintellij.simulator.services.DefaultGdbServer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope

/**
 * Owns the concrete GDB server adapter for a project simulation lifecycle.
 *
 * The core session only consumes the [com.github.jozott00.wokwiintellij.core.ports.GdbServer] port. This manager keeps
 * IntelliJ disposal registration and reuse rules with the simulator controller.
 *
 * @property project project whose plugin disposable owns created GDB servers.
 * @property childScope scope factory passed to newly created server instances.
 */
class WokwiGdbServerManager(
    private val project: Project,
    private val childScope: () -> CoroutineScope,
) {
    private var gdbServer: DefaultGdbServer? = null

    val currentServer: DefaultGdbServer?
        get() = gdbServer

    /**
     * Configures the GDB server for the next simulator runtime.
     *
     * Existing running debug servers are reused after resetting their event channel. Servers are disposed when a new
     * non-debug start is requested or when the previous server is no longer running.
     *
     * @param shouldDebug whether the next runtime needs debugger support.
     * @param port requested GDB port, or `null` to let the server choose one.
     * @return the server to attach to the session, or `null` for non-debug starts.
     */
    fun configure(shouldDebug: Boolean, port: Int?): DefaultGdbServer? {
        gdbServer?.apply {
            if (!shouldDebug || !isRunning()) {
                disposeByDisposer()
                gdbServer = null
            } else {
                resetEventChannel()
            }
        }

        if (shouldDebug && gdbServer == null) {
            gdbServer = DefaultGdbServer(childScope()).also { server ->
                Disposer.register(project.wokwiDisposable, server)
                server.listen(port)
            }
        }

        return gdbServer
    }

    /**
     * Returns the currently bound GDB port, if a server exists.
     */
    fun runningPort(): Int? = gdbServer?.getCurrentServerPort()

    /**
     * Disposes the current GDB server and clears this manager's reference to it.
     */
    fun disposeServer() {
        gdbServer?.disposeByDisposer()
        gdbServer = null
    }
}
