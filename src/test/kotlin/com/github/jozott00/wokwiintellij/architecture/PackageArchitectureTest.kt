package com.github.jozott00.wokwiintellij.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import kotlin.test.Test
import kotlin.test.assertTrue

class PackageArchitectureTest {

    @Test
    fun `core package does not depend on ide ui or browser APIs`() {
        val productionClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.github.jozott00.wokwiintellij")

        noClasses()
            .that()
            .resideInAPackage("..core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.intellij..",
                "javax.swing..",
                "java.awt..",
                "org.cef..",
            )
            .check(productionClasses)
    }

    @Test
    fun `simulator package does not depend on IntelliJ UI or browser APIs`() {
        val productionClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.github.jozott00.wokwiintellij")

        noClasses()
            .that()
            .resideInAPackage("com.github.jozott00.wokwiintellij.simulator..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.intellij..",
                "javax.swing..",
                "java.awt..",
                "org.cef..",
            )
            .check(productionClasses)
    }

    @Test
    fun `IntelliJ actions live under ide actions package`() {
        val productionClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.github.jozott00.wokwiintellij")

        assertTrue(
            productionClasses.none {
                it.packageName == "com.github.jozott00.wokwiintellij.actions" ||
                    it.packageName.startsWith("com.github.jozott00.wokwiintellij.actions.")
            },
            "Production action classes should live under com.github.jozott00.wokwiintellij.ide.actions",
        )
    }

    @Test
    fun `IntelliJ execution classes live under ide execution package`() {
        val productionClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.github.jozott00.wokwiintellij")

        assertTrue(
            productionClasses.none {
                it.packageName == "com.github.jozott00.wokwiintellij.execution" ||
                    it.packageName.startsWith("com.github.jozott00.wokwiintellij.execution.")
            },
            "Production execution classes should live under com.github.jozott00.wokwiintellij.ide.execution",
        )
    }
}
