import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.util.Properties


plugins {
  id("java") // Java support
  alias(libs.plugins.kotlin) // Kotlin support
  alias(libs.plugins.intelliJPlatform) // Gradle IntelliJ Plugin
  alias(libs.plugins.changelog) // Gradle Changelog Plugin
  alias(libs.plugins.qodana) // Gradle Qodana Plugin
  alias(libs.plugins.kover) // Gradle Kover Plugin
  alias(libs.plugins.kotlin.serialization)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
  jvmToolchain(17)
}

val integrationTestSourceSet = sourceSets.create("integrationTest")

/**
 * Local defaults for all test-task environment variables, using UTF-8 Java properties syntax.
 * Evaluated only when the test task starts, keeping credentials out of configuration-cache state.
 * The caller preserves inherited environment entries so CI secrets override local defaults.
 */
val localTestEnvironment = providers.provider {
  val propertiesFile = layout.projectDirectory.file("local.properties").asFile
  val properties = Properties()
  if (propertiesFile.isFile) {
    propertiesFile.reader(Charsets.UTF_8).use { properties.load(it) }
  }
  properties.stringPropertyNames().associateWith { properties.getProperty(it) }
}

// Shared by unit tests, integration tests and future Gradle Test tasks.
tasks.withType<Test>().configureEach {
  notCompatibleWithConfigurationCache("Tests load local environment defaults at execution without caching credential values")
  doFirst {
    val inheritedKeys = environment.keys.toSet()
    environment(localTestEnvironment.get().filterKeys { it !in inheritedKeys })
  }
}

// Configure the project's dependencies
repositories {
  mavenCentral()

  // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
  intellijPlatform {
    defaultRepositories()
  }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
  implementation(libs.arrow.core)
  implementation(libs.ktoml.core)
  implementation(libs.ktoml.file)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.archunit)
  testImplementation(libs.kotlin.test.junit)
  // The IDE supplies stdlib to the plugin, but Starter runs in a separate JVM.
  "integrationTestImplementation"(kotlin("stdlib"))
  "integrationTestImplementation"(libs.junit.jupiter)
  "integrationTestImplementation"(libs.kodein.di)
  "integrationTestImplementation"(libs.kotlinx.coroutines.core)
  "integrationTestRuntimeOnly"(libs.junit.platform.launcher)
  // Starter calls the platform reporter during shutdown even outside TeamCity.
  "integrationTestRuntimeOnly"(libs.teamcity.service.messages)

  // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
  intellijPlatform {
    create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

    // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
    bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
    bundledPlugin("com.intellij.modules.jcef")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

    pluginVerifier()
    zipSigner()
    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")
  }
}

intellijPlatform {
  pluginConfiguration {
    version = providers.gradleProperty("pluginVersion")

    description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
      val start = "<!-- Plugin description -->"
      val end = "<!-- Plugin description end -->"

      with(it.lines()) {
        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
      }
    }

    val changelog = project.changelog // local variable for configuration cache compatibility
    // Get the latest available change notes from the changelog file
    changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
      with(changelog) {
        renderItem(
          (getOrNull(pluginVersion) ?: getUnreleased())
            .withHeader(false)
            .withEmptySections(false),
          Changelog.OutputType.HTML,
        )
      }
    }

    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
      untilBuild = provider { null } // no until version constraint
    }
  }

  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
  }

  publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
    // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
    // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
    // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
    channels = providers.gradleProperty("pluginVersion")
      .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
  }

  pluginVerification {
    ides {
      recommended()
    }
  }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
  groups.empty()
  repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Live IDE tests run in their own CI job rather than through coverage verification.
kover {
  currentProject {
    instrumentation {
      disabledForTestTasks.add("integrationTest")
    }
  }
}

tasks {
  wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
  }

  publishPlugin {
    dependsOn(patchChangelog)
  }
}

intellijPlatformTesting {
  testIdeUi {
    register("integrationTest") {
      type = IntelliJPlatformType.CLion
      version = providers.gradleProperty("platformVersion")
      task {
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        useJUnitPlatform { includeEngines("junit-jupiter") }
        maxParallelForks = 1
        // Starter matching IDE 2026.2 requires Java 25; the plugin itself remains Java 17.
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
        systemProperty("wokwi.test.sandboxPlugins", sandboxPluginsDirectory.get().asFile.absolutePath)
        systemProperty("wokwi.test.pluginDirectoryName", providers.gradleProperty("pluginName").get())
        systemProperty("wokwi.test.ideVersion", providers.gradleProperty("platformVersion").get())
        systemProperty("wokwi.test.projectRoot", layout.projectDirectory.asFile.absolutePath)
        systemProperty("wokwi.test.artifacts", layout.buildDirectory.dir("wokwi-tests").get().asFile.absolutePath)
        notCompatibleWithConfigurationCache("Live simulator tests use runtime credentials and manage a separate IDE process")
      }
    }
  }
  runIde {
    register("runIdeForUiTests") {
      task {
        jvmArgumentProviders += CommandLineArgumentProvider {
          listOf(
            "-Drobot-server.port=8082",
            "-Dide.mac.message.dialogs.as.sheets=false",
            "-Djb.privacy.policy.text=<!--999.999-->",
            "-Djb.consents.confirmation.enabled=false",
          )
        }
      }

      plugins {
        robotServerPlugin()
      }
    }
  }
}
