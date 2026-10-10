# Testing

Keep fast unit and regression tests in `src/test/kotlin`. Tests that need a real
IDE, JCEF or the live simulator belong in `src/integrationTest/kotlin` and use
the reusable [simulator fixture](Simulator-Testing.md). Integration tests run
separately from ordinary `check`.

```sh
# Fast unit and regression tests:
./gradlew test

# Real IDE integration suite:
./gradlew integrationTest --no-configuration-cache

# Select a class without changing shared setup:
./gradlew integrationTest --tests '*YourFeatureTest' --no-configuration-cache
```

The plugin and unit tests target Java 17. The current JetBrains Starter framework
requires Java 25 for the integration-test worker, selected by the Gradle toolchain.
IDE integration tests also need a graphical display; CI uses Xvfb on Linux.

## Test environment

All Gradle `Test` tasks use the `localTestEnvironment` provider to load environment
defaults from the repository root's ignored `local.properties` file. This includes
unit tests, integration tests and future tasks of the same type.

```properties
# Editor/plugin license for tests that execute the live Wokwi simulator:
WOKWI_TEST_LICENSE=your-editor-license
# Additional environment variables required by other tests can go here too.
```

The provider reads UTF-8 Java properties when the test task starts and supplies
entries to its worker environment. The file is optional; inherited environment
variables take precedence, including explicitly empty values. Values use Java
properties syntax: omit shell `export` and surrounding quotes, and escape
backslashes or multiline values according to that format.

Keep this file ignored and uncommitted. It is never copied into fixture projects.
Values are not logged, and test tasks opt out of configuration caching to avoid
serializing credentials. Do not put secrets in `gradle.properties` or command-line
arguments.

CI supplies test credentials through its secret environment store. The simulator
workflow maps `${{ secrets.WOKWI_TEST_LICENSE }}` to `WOKWI_TEST_LICENSE`; it does
not need a local file. A supplied CI value always overrides a local default.
Each test's preflight determines which variables it requires: offline checks do
not require a Wokwi license, while live simulation requires an editor/plugin
license rather than a CLI token.

## Test design

Follow the [official IntelliJ Platform testing guidance](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html):
prefer focused functional tests with real platform implementations, and use IDE
integration tests for behavior that requires the separate IDE process or browser.
Keep assertions tied to observable feature behavior. Document new fixtures and
helpers with KDoc so feature tests can compose them without duplicating startup,
credentials or cleanup.

Fixture-specific hardware, firmware sources and regeneration instructions belong
in the fixture's README under `testData`, rather than the shared infrastructure
guide. Test reports record individual run results; developer guides describe the
durable setup and conventions.
