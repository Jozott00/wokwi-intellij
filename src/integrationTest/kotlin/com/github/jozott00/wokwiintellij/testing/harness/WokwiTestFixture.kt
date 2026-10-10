package com.github.jozott00.wokwiintellij.testing.harness

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/** Receiver shared by feature tests. Compose new helpers around [driver] instead of extending a base class.
 *
 * [projectDirectory] is a private copy that may be edited; [artifactsDirectory] contains this run's
 * diagnostics. The receiver and Driver references are valid only inside [runWokwiTest].
 */
class WokwiTestFixture internal constructor(
    val driver: Driver,
    val projectDirectory: Path,
    val artifactsDirectory: Path,
) {
    /** Assertions against the real rendered console of the project's Wokwi execution. */
    val console = RunConsole { driver.readWokwiConsole(driver.singleProject()) }
    /** Registered plugin actions, including process termination waits. */
    val simulator = SimulatorActions(driver, console)
}

/** Launch an isolated IDE with the built plugin and a writable fixture, then execute a receiver lambda.
 *
 * Requires WOKWI_TEST_LICENSE (an editor license, not a CLI token). Gradle's integrationTest task
 * supplies local.properties defaults, preferring inherited environment variables, and the IDE/plugin paths.
 * Each invocation checks fixture hashes, waits for indexing, installs
 * an in-memory license, and always stops the simulator and closes the IDE. IDE errors fail JUnit too.
 * The live suite runs serially because Starter's dependency container is shared within the worker.
 * [additionalPluginIds] enables plugins needed by a particular feature test beyond the minimal default set.
 */
fun runWokwiTest(project: ProjectFixture, additionalPluginIds: Set<String> = emptySet(), test: WokwiTestFixture.() -> Unit) {
    val license = System.getenv("WOKWI_TEST_LICENSE")?.takeIf { it.isNotBlank() }
        ?: error("Live Wokwi test requires WOKWI_TEST_LICENSE from the environment or local.properties (editor/plugin license, not a CLI token)")
    runIsolatedWokwiIde(project, license, additionalPluginIds, test)
}

/** Shared launch lifecycle for live tests and the license-free IDE/JCEF prerequisite smoke test.
 * A null license permits IDE inspection only; simulation tests must call [runWokwiTest].
 */
internal fun runIsolatedWokwiIde(project: ProjectFixture, license: String?, additionalPluginIds: Set<String> = emptySet(), test: WokwiTestFixture.() -> Unit) {
    val repository = Path.of(requireNotNull(System.getProperty("wokwi.test.projectRoot")))
    val artifacts = Files.createDirectories(Path.of(requireNotNull(System.getProperty("wokwi.test.artifacts")))
        .resolve("${project.name}-${UUID.randomUUID()}"))
    val copiedProject = project.copyTo(repository, artifacts.resolve("project"))
    prepareSdkFreeProject(copiedProject)
    val diagnostics = TestDiagnostics(artifacts, license)
    IdeErrors.install()
    IdeErrors.clear()
    val context = Starter.newContext(
        artifacts.fileName.toString(),
        TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(copiedProject)).withVersion(requireNotNull(System.getProperty("wokwi.test.ideVersion"))),
    )
    context.isReportPublishingEnabled = false
    context.pluginConfigurator.installPluginFromPath(Path.of(requireNotNull(System.getProperty("path.to.build.plugin"))))
    // Starter has its own plugin directory; copy dependencies prepared by the Gradle testing task.
    val ownPlugin = requireNotNull(System.getProperty("wokwi.test.pluginDirectoryName"))
    Files.list(Path.of(requireNotNull(System.getProperty("wokwi.test.sandboxPlugins")))).use { plugins ->
        plugins.filter { Files.isDirectory(it) && it.fileName.toString() != ownPlugin }
            .forEach { context.pluginConfigurator.installPluginFromDir(it) }
    }
    // Pin a built-in theme: the 2026.2 default Islands Dark references an unavailable scheme on fresh installs.
    val options = Files.createDirectories(context.paths.configDir.resolve("options"))
    options.resolve("laf.xml").writeText("""<application><component name="LafManager" autodetect="false"><laf themeId="Darcula"/></component></application>""")
    context.disableLoadShellEnv()
    var logs: Path? = null
    var failure: Throwable? = null
    /** Preserve the first failure and attach cleanup/shutdown problems as secondary evidence. */
    fun recordFailure(error: Throwable) {
        val existing = failure
        if (existing == null) failure = error else if (existing !== error) existing.addSuppressed(error)
    }
    val run = context.runIdeWithDriver(runTimeout = 5.minutes) {
        artifactsPublishingEnabled = false
        logs = logsDir
        addVMOptionsPatch {
            // The platform adds essential plugins and transitive dependencies automatically.
            // Performance Testing supplies Starter/Driver's transport, not simulator behavior.
            val pluginIds = setOf("com.github.jozott00.wokwiintellij", "org.toml.lang", "com.intellij.modules.jcef", "com.jetbrains.performancePlugin") + additionalPluginIds
            addSystemProperty("idea.load.plugins.id", pluginIds.sorted().joinToString(","))
        }
    }
    try {
        // Capture errors inside the block so Starter's finally cannot replace the original assertion.
        val result = run.useDriverAndCloseIde(takeScreenshot = false) {
            var fixture: WokwiTestFixture? = null
            try {
                step("Wait for project initialization and indexing") {
                    waitForIndicators(2.minutes, waitSmartLongEnough = false)
                }
                if (license != null) installTestLicense(license)
                fixture = WokwiTestFixture(this, copiedProject, artifacts)
                fixture.test()
            } catch (error: Throwable) {
                failure = error
                runCatching { takeScreenshot(artifacts.resolve("screenshot").toString()) }
                    .onFailure { diagnostics.write("screenshot-error.txt", it.toString()) }
            } finally {
                fixture?.let { active ->
                    runCatching { diagnostics.write("console.txt", active.console.snapshot()?.text.orEmpty()) }
                        .onFailure { diagnostics.write("console-error.txt", it.toString()) }
                    runCatching { active.simulator.stopAndAwaitTermination() }.onFailure { cleanup ->
                        recordFailure(cleanup)
                    }
                }
            }
        }
        result.failureError?.let { throw it }
        check(result.exitCode == 0) { "Test IDE exited with ${result.exitCode}" }
    } catch (error: Throwable) {
        recordFailure(error)
        runCatching { run.forceKill() }.onFailure { recordFailure(it) }
    } finally {
        logs?.let { path -> runCatching { diagnostics.copyLogs(path) }.onFailure { recordFailure(it) } }
        val errors = IdeErrors.messages()
        if (errors.isNotEmpty()) {
            diagnostics.write("ide-errors.txt", errors.joinToString("\n\n"))
            val ideFailure = AssertionError("IDE reported ${errors.size} error(s); see ${artifacts.resolve("ide-errors.txt")}")
            recordFailure(ideFailure)
        }
    }
    failure?.let {
        runCatching { diagnostics.write("failure.txt", it.stackTraceToString()) }
            .onFailure { diagnosticFailure -> it.addSuppressed(diagnosticFailure) }
        // Exceptions from remote APIs can embed invocation arguments, including a credential.
        if (license != null && it.stackTraceToString().contains(license)) throw AssertionError("Live test failed; see redacted diagnostics in $artifacts")
        throw it
    }
}

/** Open plain firmware fixtures as generic modules, avoiding IDEA's automatic Java module/JDK assignment.
 * Explicit project metadata declared by a feature fixture takes precedence over this default.
 */
internal fun prepareSdkFreeProject(project: Path) {
    val idea = Files.createDirectories(project.resolve(".idea"))
    if (Files.exists(idea.resolve("modules.xml"))) return
    idea.resolve("modules.xml").writeText("""<project version="4"><component name="ProjectModuleManager"><modules><module fileurl="file://${'$'}PROJECT_DIR${'$'}/.idea/firmware.iml" filepath="${'$'}PROJECT_DIR${'$'}/.idea/firmware.iml"/></modules></component></project>""")
    idea.resolve("firmware.iml").writeText("""<module type="EMPTY_MODULE" version="4"><component name="NewModuleRootManager"><content url="file://${'$'}MODULE_DIR${'$'}"/><orderEntry type="sourceFolder" forTests="false"/></component></module>""")
    if (!Files.exists(idea.resolve("misc.xml"))) {
        idea.resolve("misc.xml").writeText("""<project version="4"><component name="ProjectRootManager" version="2"/></project>""")
    }
}

/** Exports only selected diagnostics, redacting the license and excluding IDE config/credential stores. */
internal class TestDiagnostics(private val directory: Path, private val license: String?) {
    /** Remove credentials from text while allowing license-free smoke test diagnostics. */
    private fun redact(text: String): String = if (license.isNullOrEmpty()) text else text.replace(license, "[REDACTED]")

    /** Save UTF-8 text after removing the test credential. */
    fun write(name: String, text: String) {
        directory.resolve(name).writeText(redact(text))
    }

    /** Copy text logs from the launch's log directory; raw config, binaries and payload dumps stay private. */
    fun copyLogs(source: Path) {
        if (!Files.isDirectory(source)) return
        Files.walk(source).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().let { name -> name.endsWith(".log") || name.endsWith(".txt") } }
                .forEach { file ->
                    val target = directory.resolve("ide-logs").resolve(source.relativize(file))
                    Files.createDirectories(target.parent)
                    runCatching { target.writeText(redact(file.readText())) }
                        .onFailure { write("log-copy-error.txt", "Unable to read ${file.fileName}: ${it.javaClass.simpleName}") }
                }
        }
    }
}

/** Collect Starter's asynchronous IDE error reports so they fail the owning JUnit test after shutdown. */
internal object IdeErrors {
    private val errors = mutableListOf<String>()
    private var installed = false

    /** Install the official CIServer override once; integration tests run in a single serial worker. */
    @Synchronized fun install() {
        if (installed) return
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) {
                object : CIServer by NoCIServer {
                    /** Bridge IDE failures across the process boundary without throwing on a background thread. */
                    override fun reportTestFailure(testName: String, message: String, details: String, linkToLogs: String?, kind: TeamCityReporter.SyntheticTestKind, generifyTestName: Boolean) {
                        synchronized(errors) { errors += "$testName: $message\n$details" }
                    }
                }
            }
        }
        installed = true
    }

    /** Reset reports before launching the next isolated IDE. */
    fun clear() = synchronized(errors) { errors.clear() }
    /** Snapshot all reports after the IDE has shut down and its error collector has finished. */
    fun messages(): List<String> = synchronized(errors) { errors.toList() }
}
