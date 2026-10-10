package com.github.jozott00.wokwiintellij.testing.harness

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ProcessHandlerRef
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Persist the Remote Debug configuration described in the user guide before the copied project is opened.
 * Both Wokwi macros and the before-launch task are resolved by the real CLion runner.
 * WOKWI_TEST_GDB can select a recent target-specific GDB; otherwise CLion's bundled multi-target GDB is used.
 */
fun prepareClionRemoteDebug(project: Path) {
    val executable = System.getenv("WOKWI_TEST_GDB")?.takeIf { it.isNotBlank() }?.let {
        val path = Path.of(it)
        require(path.isAbsolute && Files.isExecutable(path)) { "WOKWI_TEST_GDB must name an executable absolute path" }
        it
    }
    val debugger = if (executable == null) "<debugger kind=\"GDB\" isBundled=\"true\" />"
                   else "<debugger kind=\"GDB\">${xmlText(executable)}</debugger>"
    // CLion's plain-folder project model has no module roots to discover top-level .run files.
    Files.createDirectories(project.resolve(".idea/runConfigurations")).resolve("Wokwi_Debug.xml").writeText("""
        <component name="ProjectRunConfigurationManager">
          <configuration name="Wokwi Debug" type="CLion_Remote" version="1"
                         remoteCommand="${'$'}WokwiGdbServer${'$'}" symbolFile="${'$'}WokwiElfPath${'$'}" sysroot="">
            $debugger
            <pathMapping remote="/wokwi-avr-fixture" local="${'$'}PROJECT_DIR${'$'}" />
            <method v="2"><option name="WokwiStartDebug.Before.Run" enabled="true" /></method>
          </configuration>
        </component>
    """.trimIndent())
}

/** Escape a local executable path in persisted XML without changing its shell interpretation. */
private fun xmlText(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** Finish CLion's first-project toolchain dialog in the fresh sandbox before invoking any execution action. */
internal fun Driver.finishClionSetup() {
    val wizard = ui.dialog(title = "Open Project Wizard")
    awaitCondition("CLion initial toolchain dialog", 20.seconds) { wizard.present() }
    awaitCondition("CLion toolchain confirmation", 20.seconds) { wizard.okButton.isEnabled() }
    withContext(OnDispatcher.EDT) { cast(wizard.okButton.component, DebugSetupButton::class).doClick() }
    awaitCondition("CLion toolchain dialog to close", 15.seconds) { !wizard.present() }
}

/** Composed debugger operations backed by actual CLion sessions and rendered variable values.
 * Valid only inside the owning fixture lambda. Source lines in this API are one-based.
 */
class DebuggerActions internal constructor(
    private val driver: Driver,
    private val simulator: SimulatorActions,
    private val projectDirectory: Path,
    private val artifacts: Path,
) {
    /** Set a source breakpoint through the platform's normal breakpoint API in the isolated project. */
    fun setBreakpoint(file: Path, line: Int) {
        require(line > 0) { "Source lines are one-based" }
        require(file.startsWith(projectDirectory)) { "Breakpoints must belong to the copied project" }
        val virtualFile = requireNotNull(driver.utility(DebugFileSystem::class).getInstance()
            .refreshAndFindFileByPath(file.toString())) { "Source file is absent from the IDE VFS: $file" }
        driver.withReadAction(OnDispatcher.EDT) {
            utility(DebugBreakpointUtil::class).getInstance().toggleLineBreakpoint(singleProject(), virtualFile, line - 1, false)
        }
    }

    /** Select the fixture's persisted configuration and invoke CLion's normal Debug action. */
    fun launch(configurationName: String = "Wokwi Debug") {
        simulator.ensureToolWindowOpen()
        driver.withContext(OnDispatcher.EDT) {
            val manager = utility(DebugRunManager::class).getInstance(singleProject())
            val configurations = manager.getAllSettings()
            val configuration = requireNotNull(configurations.singleOrNull { it.getName() == configurationName }) {
                "Missing '$configurationName' configuration; loaded: ${configurations.map { it.getName() }}"
            }
            manager.setSelectedConfiguration(configuration)
        }
        driver.invokeAction("Debug", component = driver.ideFrame().component)
    }

    /** Wait for a real suspended session at the exact copied source path and line, failing on debugger exit. */
    fun awaitSuspendedAt(file: Path, line: Int, timeout: Duration = 60.seconds) {
        var observation = "No debug session"
        awaitCondition("debugger suspension at ${file.fileName}:$line", timeout, { observation }) {
            driver.withReadAction(OnDispatcher.EDT) {
                val session = sessions().singleOrNull() ?: return@withReadAction false
                check(!session.isStopped()) {
                    "Debugger exited before ${file.fileName}:$line"
                }
                val position = session.getCurrentPosition()
                val path = position?.getFile()?.getPath()
                val actualLine = position?.getLine()?.plus(1)
                observation = "suspended=${session.isSuspended()}, position=$path:$actualLine"
                session.isSuspended() && path == file.toString() && actualLine == line
            }
        }
        artifacts.resolve("debugger-position.txt").writeText(observation)
    }

    /** Assert a decimal scalar in the actual debugger Variables tree, retaining the last rendering on failure. */
    fun awaitVariable(name: String, value: Int, timeout: Duration = 15.seconds) {
        val tree = driver.ideFrame().tree("//div[@class='XDebuggerTree']")
        val expected = Regex("^\\s*${Regex.escape(name)}\\s*=\\s*(?:\\{[^}]*}\\s*)?$value\\s*$")
        var rows = emptyList<String>()
        try {
            awaitCondition("debugger variable $name = $value", timeout, { rows.joinToString("\n") }) {
                rows = tree.collectExpandedPaths().map { it.path.last() }
                rows.any { expected.containsMatchIn(it) }
            }
        } finally {
            artifacts.resolve("debugger-variables.txt").writeText(rows.joinToString("\n"))
        }
    }

    /** Request native source stepping. Pair with awaitSuspendedAt at the expected next source line. */
    fun stepOver() = driver.withContext(OnDispatcher.EDT) { activeSession().stepOver(false) }

    /** Continue the existing native debug session so the firmware can produce observable UART output. */
    fun resume() = driver.withContext(OnDispatcher.EDT) { activeSession().resume() }

    /** Stop every native debug session and wait for its underlying debugger process to terminate. */
    fun stopAndAwaitTermination(timeout: Duration = 15.seconds) {
        val active = driver.withContext(OnDispatcher.EDT) { sessions() }
        if (active.isEmpty()) return
        val processes = driver.withContext(OnDispatcher.EDT) { active.map { it.getDebugProcess().getProcessHandler() } }
        driver.withContext(OnDispatcher.EDT) { active.forEach { it.stop() } }
        awaitCondition("native debugger process termination", timeout) {
            driver.withContext(OnDispatcher.EDT) {
                active.all { it.isStopped() } && processes.all { it.isProcessTerminated() }
            }
        }
    }

    /** Resolve sessions inside the isolated IDE, independent of tool-window focus. */
    private fun sessions(): List<DebugSessionAccess> = driver.utility(DebugManagerAccess::class)
        .getInstance(driver.singleProject()).getDebugSessions().toList()

    /** Reject missing/ambiguous sessions instead of sending an action to an unrelated execution. */
    private fun activeSession(): DebugSessionAccess = sessions().single()
}

/** Public run-manager access for selecting an existing persisted configuration. */
@Remote("com.intellij.execution.RunManager")
internal interface DebugRunManager {
    fun getInstance(project: Project): DebugRunManager
    fun getAllSettings(): List<DebugRunConfiguration>
    fun setSelectedConfiguration(configuration: DebugRunConfiguration)
}

/** Opaque run configuration settings with the user-visible name. */
@Remote("com.intellij.execution.RunnerAndConfigurationSettings")
internal interface DebugRunConfiguration {
    fun getName(): String
}

/** Public native-debugger session discovery, executed on EDT. */
@Remote("com.intellij.xdebugger.XDebuggerManager")
internal interface DebugManagerAccess {
    fun getInstance(project: Project): DebugManagerAccess
    fun getDebugSessions(): Array<DebugSessionAccess>
}

/** Narrow public platform session API; assertions observe actual native debugger state. */
@Remote("com.intellij.xdebugger.XDebugSession")
internal interface DebugSessionAccess {
    fun isSuspended(): Boolean
    fun isStopped(): Boolean
    fun getCurrentPosition(): DebugSourcePosition?
    fun getDebugProcess(): DebugProcessAccess
    fun stepOver(ignoreBreakpoints: Boolean)
    fun resume()
    fun stop()
}

/** Native process lifetime, accessed on the backend without the split debugger's deprecated UI descriptor API. */
@Remote("com.intellij.xdebugger.XDebugProcess")
internal interface DebugProcessAccess {
    fun getProcessHandler(): ProcessHandlerRef
}

/** Source location resolved by GDB's debug symbols and CLion's source mapping. */
@Remote("com.intellij.xdebugger.XSourcePosition")
internal interface DebugSourcePosition {
    fun getLine(): Int
    fun getFile(): VirtualFile
}

/** Absolute VFS lookup also works for CLion projects without IntelliJ module content roots. */
@Remote("com.intellij.openapi.vfs.LocalFileSystem")
internal interface DebugFileSystem {
    fun getInstance(): DebugFileSystem
    fun refreshAndFindFileByPath(path: String): VirtualFile?
}

/** Platform breakpoint creation, without Driver SDK's IntelliJ-module-relative source lookup. */
@Remote("com.intellij.xdebugger.XDebuggerUtil")
internal interface DebugBreakpointUtil {
    fun getInstance(): DebugBreakpointUtil
    fun toggleLineBreakpoint(project: Project, file: VirtualFile, line: Int, temporary: Boolean)
}

/** Invoke the standard dialog button handler without relying on desktop scaling or pointer position. */
@Remote("javax.swing.AbstractButton")
internal interface DebugSetupButton {
    fun doClick()
}
