# Simulator Integration Tests

The integration suite uses JetBrains Starter to launch an isolated IDE and Driver
to invoke registered plugin actions and read the actual Run console. It is kept
in `src/integrationTest/kotlin`, separate from the existing JUnit 4 unit tests and
from ordinary `check`. Every public helper and remote adapter has KDoc explaining
its purpose and lifetime.

Under `com.github.jozott00.wokwiintellij.testing`, packages separate the reusable
infrastructure from its validation, concrete project definitions and feature tests:

| Package | Responsibility |
| --- | --- |
| `harness` | IDE lifecycle, receiver fixture, project-copy model, actions, console assertions and adapters |
| `harness.tests` | Offline harness checks and IDE infrastructure smoke tests |
| `fixtures` | Named project definitions referring to inputs under `testData/simulator` |
| `testing` root | Tests of plugin behavior using the harness and project definitions |

The harness accepts a generic `ProjectFixture` and does not depend on named
fixtures or test classes. Concrete firmware and regeneration instructions remain
in `testData/simulator`, outside the Kotlin packages.

This follows the official [integration-test setup](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html),
[API interaction](https://plugins.jetbrains.com/docs/intellij/integration-tests-api.html)
and [UI testing guidance](https://plugins.jetbrains.com/docs/intellij/integration-tests-ui.html).
The Gradle `intellijPlatformTesting.testIdeUi` task supplies the built plugin;
Starter owns IDE startup and shutdown. The framework dependencies match the IDE
build. The test runtime explicitly supplies Kotlin stdlib and TeamCity service
messages needed by Starter’s shutdown reporter, including outside TeamCity. The current Starter requires Java 25 for its test worker; the distributed
plugin retains its Java 17 target. The worker selects only the Jupiter engine,
so bundled Vintage tests do not interfere with discovery.

## Prerequisites

See [Testing](Testing.md) for Gradle commands, source-set conventions and shared
environment setup.

Live tests require an editor/plugin license in `WOKWI_TEST_LICENSE` (not a CLI
token). Shared local environment setup and CI precedence are documented in
[Testing](Testing.md#test-environment). A missing or blank license fails preflight
rather than silently skipping a requested test.

An IDE with JCEF-enabled JetBrains Runtime, the TOML/JCEF plugin dependencies,
network access to Wokwi and a graphical display are required. On Linux, run with
`xvfb-run -a`. Starter downloads/caches its IDE independently from the unit-test
sandbox. Each invocation receives a unique project copy and IDE context. JUnit
parallel execution is disabled because Starter's DI container and error reporter
are shared within a worker.

The simulator GitHub Actions workflow caches Starter's IDE archives in
`out/ide-tests/installers` and extracted installations in
`out/ide-tests/cache/builds`, separately from Gradle's dependency cache. The key
includes runner OS/architecture and `platformType`/`platformVersion` from
`gradle.properties`. The first successful run populates the cache; later cache
hits reuse the download and extracted IDE. An IDE version change or cache
eviction requires a fresh download. Cache restore still transfers data, so
compare cold and warm workflow durations to measure the benefit.

Only files declared in `SHA256SUMS`, plus the manifest itself, enter that project
copy. Ignored `.idea` settings and other undeclared local files stay out, avoiding
accidental SDK assignment and indexing from a developer's fixture checkout.
Plain firmware projects receive a generic `EMPTY_MODULE` with no project SDK,
so IDEA does not create a Java module and automatically assign/index a local JDK.
Fixture authors do not need to create or commit `.idea`. The harness generates
`modules.xml`, `firmware.iml` and `misc.xml` inside the temporary project's `.idea`
directory: these describe the module, its content root and the SDK-free project.
Local workspace, VCS and other editor settings are unnecessary and remain ignored.
The IDE still runs on JetBrains Runtime, which provides JCEF. Fixtures that need
specific module/SDK configuration can declare their project metadata in the
manifest; an existing `modules.xml` takes precedence over this default. That is
an explicit exception: add only the required metadata to Git (using `git add -f`
for those specific files), and include it in `SHA256SUMS`.

The IDE does not inherit the developer's installed plugins or configuration.
By default it loads Wokwi, TOML, JCEF and Performance Testing (Starter/Driver's
transport), plus the platform's essential plugins and required transitive
dependencies. The runner sets `idea.load.plugins.id` on this launch only, using
the platform's plugin-subset selection. This keeps unrelated bundled Ultimate
plugins out of the test run without maintaining a version-dependent denylist.
It still downloads the full IDE distribution. Validate this internal platform
setting when upgrading the target IDE.
Feature tests needing another plugin can opt in:

```kotlin
runWokwiTest(projectFixture, additionalPluginIds = setOf("com.intellij.java")) {
    // Feature assertions using the additional plugin's capabilities.
}
```

## Feature-test API

```kotlin
@Tag("live-wokwi")
class FeatureTest {
    @Test fun `simulation produces expected output`() =
        runWokwiTest(projectFixture) {
            simulator.start()
            console.awaitText("Expected feature output\n")
            simulator.stopAndAwaitTermination()
        }
}
```

The fixture is the receiver of the lambda. It remains valid only within that
lambda; do not save Driver references for later tests. It exposes a small set of
composed helpers rather than requiring inheritance or a general scenario DSL.

| API | Purpose |
| --- | --- |
| `ProjectFixture` / `Fixtures` | Describe committed project inputs, verify hashes, create a writable copy |
| `runWokwiTest` | Validate credentials, launch the IDE, install the plugin, run the block and guarantee cleanup |
| `WokwiTestFixture` | Receiver exposing Driver, copied project path, artifacts path, console and simulator actions |
| `SimulatorActions` | Show the Wokwi tool window, then start, restart, toggle watch and stop through registered plugin actions |
| `RunConsole` | Read rendered output, wait for text, capture checkpoints and detect premature process exit |
| `ConsoleSnapshot` / `ConsoleCheckpoint` | Execution identity, accumulated text and output coordinates |
| `TestDiagnostics` | Export selected text diagnostics after credential redaction |
| `IdeErrors` | Bridge Starter's asynchronous IDE error reports into the owning JUnit result |
| `IdeAdapters` | Narrow public-platform remote interfaces for console access and memory-only PasswordSafe setup |
| `TimestampedStepsProvider` | Driver step logging with wall-clock timestamps and elapsed durations |

`simulator.start()` first waits for Wokwi's tool window registration, opens it,
and waits for it to be visible. JCEF renders and simulation starts only while that view is shown;
invoking Start with a hidden window can leave an active process with no UART
output. Tests may also call `simulator.ensureToolWindowOpen()` explicitly after
UI operations that hide the view.

Feature tests assert observable results from actual firmware execution, such as
UART text rendered in the IDE console. A session's “started” callback or a fake
UART event cannot satisfy those assertions. Define small, scenario-specific
expectations directly in each feature test and check clean termination. A fixture
can support multiple tests with different assertions. Larger golden outputs may
live in optional fixture files, loaded explicitly by tests that need them; the
harness does not require an expected-output file.

`RunConsole` waits for accumulated exact text, including text arriving across
multiple writes. It preserves line endings. It fails early if the process exits
before the assertion and otherwise reports a bounded console tail on timeout.
Checkpoints store execution identity and the existing output prefix. For a reused
console they exclude old output; a new execution or changed document prefix uses
the current document. If the same execution clears and restores an identical
prefix, the helper conservatively waits for additional output. A future feature
that requires detecting that exact case should add document-generation tracking.

## Add a feature test

1. Add a folder under `testData/simulator` with `wokwi.toml`, `diagram.json`,
   firmware/ELF, firmware source, a regeneration script and README.
   Commit precompiled artifacts so contributors do not need every embedded toolchain.
   Omit `.idea`; the harness creates the IDE project configuration automatically.
2. Create `SHA256SUMS` covering every simulation input and any optional golden
   files. Add a `ProjectFixture` descriptor to `Fixtures` in the `fixtures`
   package. Hashes are checked before IDE startup.
3. Add a Jupiter test at the `testing` root, tagged `live-wokwi`, using
   `runWokwiTest(Fixtures.yourFixture)`.
   Assert a feature-specific observable result, then stop or rely on guaranteed cleanup.
4. Add a focused helper when existing operations cannot express the feature.
   Put it in `harness`, compose it around `driver` or another helper and document its responsibilities
   and lifetime with KDoc. Keep Driver/version details inside adapters.

For restart coverage, take `val before = console.checkpoint()`, invoke
`simulator.restart()`, then assert the test's expected text with
`console.awaitText("Expected feature output\n", after = before)`.
For file-edit/watch coverage, introduce a focused project-edit helper that refreshes
VFS and waits for a reload. Debugger, serial-input and custom-chip helpers can be
added the same way when their first tests need them. Remote calls must use public
methods and supported return types; Driver cannot directly call suspend functions.
[Official remote API restrictions](https://plugins.jetbrains.com/docs/intellij/integration-tests-api.html).

Keep concrete test scenarios, hardware details, expected messages and binary
regeneration instructions in their tests and fixture READMEs. This guide covers
the reusable harness and shared conventions. Mutations operate on copied files,
never the committed fixture. Keep expectations in the feature test or explicitly
loaded golden files rather than synthesizing success inside the harness.
[Official testdata guidance](https://plugins.jetbrains.com/docs/intellij/test-project-and-testdata-directories.html).

## Lifecycle, failures and credentials

The runner pins the isolated IDE to the built-in Darcula theme. The fresh 2026.2
default Islands Dark theme can refer to a missing editor scheme; choosing a stable
theme avoids that startup error without suppressing IDE exception reporting.

The runner waits for project initialization/indexing using Driver's
`waitForIndicators(..., waitSmartLongEnough = false)`. It skips Driver's default
10-second idle period and instead checks tool window registration and visibility
before simulator startup. These checks wait for observable prerequisites without
a fixed post-loading delay. Features needing further readiness must add focused
waits for their own UI or services.

`TimestampedStepsProvider` is registered through Driver's `StepsProvider`
ServiceLoader extension in the integration-test resources. It timestamps SDK and
harness steps with local wall-clock time (including milliseconds) and reports
monotonic elapsed durations on completion or failure. It flushes
`[HH:mm:ss.SSS]: Step '…' ... ` at the start, then appends `finished (… ms)` or
`failed (… ms)` and a newline when the step ends. This makes initialization,
tool window opening and action timings comparable with Starter's console logs.
It preserves the original exception while omitting exception text from step logs.

The runner installs credentials in the
isolated IDE's PasswordSafe with `memoryOnly=true`, and executes the receiver
block in a Driver context. It stops an active process in `finally` and closes the
IDE through Starter, forcing termination if shutdown fails. Cleanup failures are
attached to the original assertion rather than replacing it. The same lifecycle
can launch the IDE for infrastructure checks without installing a license.

`IdeErrors` overrides `CIServer.reportTestFailure` through Starter's DI container
and collects failures from background reporting threads. After shutdown, any IDE
error makes the JUnit test fail. This matters because exceptions in the separate
IDE process do not automatically propagate to the test JVM.
[Official IDE exception handling](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html#catching-exceptions-from-ide).

Each invocation writes diagnostics under `build/wokwi-tests/<fixture>-<uuid>`:
rendered console text, assertion details, IDE errors, selected text logs and a
screenshot on failure when available. Text exports redact the test license. The
CI workflow uploads only these exports and JUnit reports, excluding raw IDE
configuration/system directories and PasswordSafe stores. Do not upload the
Starter cache or raw environment. Copied projects remain locally available for
reproduction and can be removed with the build directory.

`.github/workflows/run-ui-tests.yml` runs the suite manually on Linux under Xvfb,
using the repository secret `WOKWI_TEST_LICENSE`. Starter replaces the previous
Remote Robot startup/health-check orchestration. Start with this single platform;
expand the matrix after real JCEF/simulator runs establish stability. Do not expose
credentials to untrusted pull-request contexts. Network or licensing failures
remain visible failures; the harness does not retry a whole simulation until green.

Keep core/model tests as the fast default. Full IDE tests cover behaviors that
need the browser and simulator; they supplement the official preference for
functional tests using real platform implementations.
[Official plugin testing guidance](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html).
