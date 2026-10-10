package com.github.jozott00.wokwiintellij.testing.harness

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ToolWindowManager
import com.intellij.driver.sdk.getRunContentManager
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openToolWindow
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.ideFrame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** User-facing simulator actions. Action IDs are the same ones registered in plugin.xml. */
class SimulatorActions internal constructor(private val driver: Driver, private val console: RunConsole) {
    /** Show the simulator view so JCEF renders, then start normally and wait for an attached process. */
    fun start(timeout: Duration = 30.seconds) {
        ensureToolWindowOpen(timeout)
        invoke("Start")
        awaitCondition("Wokwi execution descriptor", timeout) { console.snapshot() != null }
    }

    /** Await registration before opening the Wokwi view; JCEF starts rendering only while visible. */
    fun ensureToolWindowOpen(timeout: Duration = 15.seconds) {
        driver.step("Wait for Wokwi Simulator tool window registration") {
            awaitCondition("Wokwi tool window registration", timeout) {
                driver.withContext(OnDispatcher.EDT) {
                    utility(ToolWindowManager::class).getInstance(singleProject()).getToolWindow("Wokwi Simulator") != null
                }
            }
        }
        driver.openToolWindow("Wokwi Simulator")
        awaitCondition("Wokwi tool window visibility", timeout) {
            driver.withContext(OnDispatcher.EDT) { getToolWindow("Wokwi Simulator").isVisible() }
        }
    }

    /** Request the normal toolbar restart; use a console checkpoint to assert new firmware output. */
    fun restart() = invoke("Restart")

    /** Toggle the normal firmware watch action for tests of automatic reload. */
    fun toggleWatch() = invoke("Watch")

    /** Stop through the toolbar and wait until the underlying Run process is terminated. */
    fun stopAndAwaitTermination(timeout: Duration = 15.seconds) {
        if (console.snapshot()?.terminated != false) return
        invoke("Stop")
        awaitCondition("simulator process termination", timeout) { console.snapshot()?.terminated == true }
        check(console.snapshot()?.exitCode == 0) { "Stop must terminate the Wokwi process successfully" }
    }

    /** Supply the IDE frame as the action data context so it resolves the test project. */
    private fun invoke(name: String) {
        driver.invokeAction("com.github.jozott00.wokwiintellij.actions.Wokwi${name}Action", component = driver.ideFrame().component)
    }
}

/** Read the unique Wokwi descriptor in this project on EDT with platform read access. */
internal fun Driver.readWokwiConsole(project: Project): ConsoleSnapshot? = withReadAction(OnDispatcher.EDT) {
    val descriptors = getRunContentManager(project).getAllDescriptors().filter { it.getDisplayName() == "Wokwi Simulator" }
    check(descriptors.size <= 1) { "Ambiguous Wokwi Run console: ${descriptors.size} descriptors" }
    val descriptor = descriptors.singleOrNull() ?: return@withReadAction null
    val process = descriptor.getProcessHandler() ?: return@withReadAction null
    val executionConsole = cast(descriptor, ConsoleDescriptor::class).getExecutionConsole()
    val editor = executionConsole?.let { cast(it, RenderedConsole::class).getEditor() }
    ConsoleSnapshot(descriptor.getExecutionId(), editor?.getDocument()?.getText().orEmpty(), process.isProcessTerminated(), process.getExitCode())
}

/** Configure only the isolated IDE's in-memory PasswordSafe entry, without touching the user's keychain. */
internal fun Driver.installTestLicense(license: String) {
    try {
        val attributes = new(LicenseAttributes::class, "WokwiIntellij", "WokwiLicense")
        val credentials = new(LicenseCredentials::class, "WokwiLicense", license)
        val safe = utility(PasswordSafeAccess::class).getInstance()
        safe.set(attributes, credentials, true)
        check(safe.isPasswordStoredOnlyInMemory(attributes, credentials)) { "Test credential must stay in memory" }
    } catch (_: Throwable) {
        // Remote constructor exceptions may include arguments; never attach an unredacted cause.
        throw IllegalStateException("Unable to install the test license in the isolated IDE's memory-only PasswordSafe")
    }
}

/** Public platform descriptor methods omitted by Driver SDK's smaller descriptor interface. */
@Remote("com.intellij.execution.ui.RunContentDescriptor")
internal interface ConsoleDescriptor {
    /** Return the execution console attached by the plugin's real runner. */
    fun getExecutionConsole(): ExecutionConsole?
}

/** Opaque remote reference used solely for casting the execution console to its concrete implementation. */
@Remote("com.intellij.execution.ui.ExecutionConsole")
internal interface ExecutionConsole

/** Adapter to the editor containing rendered console output, including process-handler writes. */
@Remote("com.intellij.execution.impl.ConsoleViewImpl")
internal interface RenderedConsole {
    /** Return the console editor once the UI has created it. */
    fun getEditor(): Editor?
}

/** Remote constructor marker for the platform credential lookup key. */
@Remote("com.intellij.credentialStore.CredentialAttributes")
internal interface LicenseAttributes

/** Remote constructor marker for credentials; no secret is returned to test diagnostics. */
@Remote("com.intellij.credentialStore.Credentials")
internal interface LicenseCredentials

/** Public PasswordSafe API used to insert credentials into memory only. */
@Remote("com.intellij.ide.passwordSafe.PasswordSafe")
internal interface PasswordSafeAccess {
    /** Resolve the application PasswordSafe singleton inside the isolated IDE. */
    fun getInstance(): PasswordSafeAccess
    /** Store a credential with memoryOnly=true to avoid persistent credential stores. */
    fun set(attributes: LicenseAttributes, credentials: LicenseCredentials?, memoryOnly: Boolean)
    /** Verify the entry is available only from the memory overlay before simulation begins. */
    fun isPasswordStoredOnlyInMemory(attributes: LicenseAttributes, credentials: LicenseCredentials): Boolean
}
