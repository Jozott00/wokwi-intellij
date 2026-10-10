package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.ports.GdbServer
import com.github.jozott00.wokwiintellij.extensions.DisposableRef
import com.github.jozott00.wokwiintellij.extensions.asDisposableRef
import com.github.jozott00.wokwiintellij.extensions.wokwiDisposable
import com.github.jozott00.wokwiintellij.ide.services.IntelliJUserNotifier
import com.github.jozott00.wokwiintellij.services.UserNotifier
import com.github.jozott00.wokwiintellij.simulator.services.DefaultGdbServer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope

/**
 * Controller-facing GDB server manager interface.
 */
interface GdbServerManager {
    /**
     * Configures the GDB server for the next simulator runtime.
     *
     * @param shouldDebug whether the next runtime needs debugger support.
     * @param port requested GDB port, or `null` to let the server choose one.
     * @return the server to attach to the session, or `null` for non-debug starts.
     */
    fun configure(shouldDebug: Boolean, port: Int?): GdbServer?

    /**
     * Returns the currently bound GDB port, if a server is running.
     */
    fun runningPort(): Int?

    /**
     * Disposes the current GDB server and clears this manager's reference to it.
     */
    fun disposeServer()
}

/**
 * Owns the concrete GDB server adapter for a project simulation lifecycle.
 *
 * The core session only consumes the [GdbServer] port. This manager keeps
 * IntelliJ disposal registration and reuse rules with the simulator controller.
 *
 * @property project project whose plugin disposable owns created GDB servers.
 * @param coroutineScope scope passed directly to [DefaultGdbServer] (it owns its own child job).
 * @property userNotifier notifier used to report GDB server errors to the user.
 */
class WokwiGdbServerManager(
    private val project: Project,
    coroutineScope: CoroutineScope,
    private val userNotifier: UserNotifier = IntelliJUserNotifier,
) : GdbServerManager {

    private val scope = coroutineScope
    private var gdbServer: DisposableRef<DefaultGdbServer>? = null

    private val currentServer: DefaultGdbServer?
        get() = gdbServer?.value

    @Synchronized
    override fun configure(shouldDebug: Boolean, port: Int?): GdbServer? {
        val server = currentServer

        if (shouldDebug) {
            // Reuse running server when port is null or matches the actual bound port
            if (server != null && server.isRunning()) {
                val boundPort = server.getCurrentServerPort()
                if (port == null || port == boundPort) {
                    // Reuse existing server — do NOT reset event channel
                    return server
                }
                // Requested port differs from bound port — dispose and recreate
                disposeServer()
            } else if (server != null) {
                // Server exists but is no longer running
                disposeServer()
            }

            // Create a new server
            val newServer = DefaultGdbServer(scope)
            val ref = newServer.asDisposableRef()
            Disposer.register(project.wokwiDisposable, ref)

            val result = newServer.listen(port)
            if (result.isSuccess) {
                gdbServer = ref
                return newServer
            } else {
                Disposer.dispose(ref)
                gdbServer = null
                userNotifier.error(
                    title = "Couldn't start GDB server",
                    message = result.exceptionOrNull()?.message ?: "Unknown error",
                )
                return null
            }
        } else {
            // Non-debug: close any existing server
            disposeServer()
            return null
        }
    }

    @Synchronized
    override fun runningPort(): Int? = currentServer?.getCurrentServerPort()

    @Synchronized
    override fun disposeServer() {
        gdbServer?.let { Disposer.dispose(it) }
        gdbServer = null
    }
}
