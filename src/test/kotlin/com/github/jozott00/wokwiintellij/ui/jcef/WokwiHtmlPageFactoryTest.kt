package com.github.jozott00.wokwiintellij.ui.jcef

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class WokwiHtmlPageFactoryTest {

    @Test
    fun `creates wrapper page with generated Wokwi iframe URL`() {
        val page = WokwiHtmlPageFactory().createPage(
            WokwiHtmlPageFactory.Options(
                wokwiHost = "https://example.wokwi.test/",
                extensionVersion = "1.2.3",
                gitHash = "abc123",
                licenseUserId = "user 42",
            )
        )

        assertContains(page, """data-src="https://example.wokwi.test/vscode/wcode?v=1.2.3&g=abc123&u=user+42"""")
    }

    @Test
    fun `inlines wrapper resources without leaving template placeholders`() {
        val page = WokwiHtmlPageFactory().createPage()

        assertContains(page, "window.__WokwiIntellij")
        assertContains(page, "iframe")
        assertFalse(page.contains("{{WOKWI_IFRAME_URL}}"))
        assertFalse(page.contains("{{BRIDGE_CSS}}"))
        assertFalse(page.contains("{{BRIDGE_JS}}"))
    }
}
