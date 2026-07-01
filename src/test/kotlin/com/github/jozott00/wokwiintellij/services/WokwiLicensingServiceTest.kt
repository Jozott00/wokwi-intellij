package com.github.jozott00.wokwiintellij.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WokwiLicensingServiceTest {

    @Test
    fun `reports missing license`() = runBlocking {
        val service = serviceWithLicense(null)

        val result = service.loadAndCheckLicense()

        val error = result.fold({ it }, { null })
        assertEquals("No Wokwi license found", error?.title)
        assertEquals("Set your Wokwi license in the Wokwi window.", error?.message)
    }

    @Test
    fun `reports invalid license`() = runBlocking {
        val service = serviceWithLicense("not a license")

        val result = service.loadAndCheckLicense()

        val error = result.fold({ it }, { null })
        assertEquals("Invalid Wokwi license", error?.title)
        assertEquals("The Wokwi license could not be parsed.", error?.message)
    }

    @Test
    fun `reports expired license`() = runBlocking {
        val service = serviceWithLicense(license(expiration = "20000101"))

        val result = service.loadAndCheckLicense()

        val error = result.fold({ it }, { null })
        assertEquals("Expired Wokwi license", error?.title)
        assertEquals("The Wokwi license is expired, please refresh it.", error?.message)
    }

    @Test
    fun `accepts valid license`() = runBlocking {
        val rawLicense = license(expiration = "20991231")
        val service = serviceWithLicense(rawLicense)

        val result = service.loadAndCheckLicense()

        assertEquals(rawLicense, result.fold({ null }, { it }))
    }

    @Test
    fun `returns null when parsing license without null terminator`() {
        val service = serviceWithLicense(null)
        val encodedLicense = Base64.getEncoder().encodeToString(
            "u=12345678&n=Test&e=test@example.com&x=20991231".toByteArray(StandardCharsets.UTF_8)
        )

        val parsed = service.parseLicense(encodedLicense)

        assertNull(parsed)
    }

    private fun serviceWithLicense(license: String?) =
        WokwiLicensingService.createForTests(
            cs = CoroutineScope(Dispatchers.Unconfined),
            initialLicense = license,
        )

    private fun license(expiration: String): String {
        val payload = "u=12345678&n=Test&e=test@example.com&x=$expiration&p=pro"
            .toByteArray(StandardCharsets.UTF_8) + byteArrayOf(0)
        return Base64.getEncoder().encodeToString(payload)
    }
}
