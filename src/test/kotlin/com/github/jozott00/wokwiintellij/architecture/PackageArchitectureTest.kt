package com.github.jozott00.wokwiintellij.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import kotlin.test.Test

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
    fun `simulator services do not depend on IntelliJ UI or browser APIs`() {
        val productionClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.github.jozott00.wokwiintellij")

        noClasses()
            .that()
            .resideInAPackage("..simulator.services..")
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
}
