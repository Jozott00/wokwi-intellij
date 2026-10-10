package com.github.jozott00.wokwiintellij.testing.harness.tests

import com.github.jozott00.wokwiintellij.testing.fixtures.Fixtures
import com.github.jozott00.wokwiintellij.testing.harness.awaitCondition
import com.github.jozott00.wokwiintellij.testing.harness.installTestLicense
import com.github.jozott00.wokwiintellij.testing.harness.runIsolatedWokwiIde
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ActionManager
import com.intellij.driver.sdk.ProjectRootManager
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.model.OnDispatcher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** License-free smoke coverage of IDE launch, plugin installation, JCEF availability and shutdown. */
@Tag("ide-smoke")
class IdeInfrastructureTest {
    /** Check prerequisites in the real IDE before a live test attempts to execute firmware. */
    @Test fun isolatedIDELoadsWokwiAndSupportsJCEF() = runIsolatedWokwiIde(Fixtures.avrUart, null) {
        driver.withReadAction(OnDispatcher.EDT) {
            assertNull(service(ProjectRootManager::class, singleProject()).getProjectSdk(), "Plain firmware fixtures must not assign a project SDK")
        }
        // A dummy value checks remote constructors and memory-only storage without a real secret.
        driver.installTestLicense("fixture-only-invalid-license")
        assertTrue(driver.utility(JcefSupport::class).isSupported(), "The test IDE must have JCEF support")
        driver.withContext(OnDispatcher.EDT) {
            assertNotNull(service(ActionManager::class).getAction("com.github.jozott00.wokwiintellij.actions.WokwiStartAction"))
        }
        // Invalid credentials exercise Start and the console adapter without contacting the simulator.
        simulator.start()
        awaitCondition("invalid-license process termination", 15.seconds) { console.snapshot()?.terminated == true }
        assertEquals(1, console.snapshot()?.exitCode, "Invalid-license startup must end with CONFIG_ERROR")
    }
}

/** Public JCEF capability query executed inside the target IDE and its JetBrains Runtime. */
@Remote("com.intellij.ui.jcef.JBCefApp", plugin = "com.intellij.modules.jcef/intellij.platform.ui.jcef")
internal interface JcefSupport {
    /** True only if this IDE runtime supports the embedded Chromium browser required by Wokwi. */
    fun isSupported(): Boolean
}
