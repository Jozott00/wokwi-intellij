package com.github.jozott00.wokwiintellij.services

import ai.grazie.utils.mpp.Base64
import com.github.jozott00.wokwiintellij.WokwiConstants
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.*


@Service(Service.Level.APP)
class WokwiLicensingService(private val cs: CoroutineScope) {

    private val licenseAttributes =
        CredentialAttributes(WokwiConstants.WOWKI_PLUGIN_SERVICE_NAME, WokwiConstants.WOKWI_LICENCE_STORE_KEY)

    private var licenseCache: String? = null

    suspend fun getLicense() = licenseCache ?: withContext(Dispatchers.IO) {
        PasswordSafe.instance.let {
            licenseCache = it.getPassword(licenseAttributes)
            licenseCache
        }
    }

    fun updateLicense(license: String) = cs.launch(Dispatchers.IO) {
        LOG.info("Update Wokwi license")
        licenseCache = license
        PasswordSafe.instance.setPassword(licenseAttributes, license)
        WokwiNotifier.notifyBalloonAsync("New Wokwi license activated", "You are ready to go!")
    }

    fun removeLicense() = cs.launch(Dispatchers.IO) {
        licenseCache = null
        PasswordSafe.instance.setPassword(licenseAttributes, null)
        WokwiNotifier.notifyBalloonAsync("Wokwi license removed", "Your license has been removed.")
    }


    fun parseLicense(license: String): WokwiLicense? {
        lateinit var decoded: ByteArray
        try {
            // Decoding the base64 input
            val decodedString = base64DblClickDecode(license)
            decoded = Base64.decode(decodedString)
        } catch (e: Exception) {
            return null
        }

        // Finding the first null byte
        val zeroIndex = decoded.indexOf(0)
        if (zeroIndex < 0) {
            return null
        }

        // Parsing the URL parameters
        val licenseText = String(decoded.sliceArray(0 until zeroIndex), StandardCharsets.UTF_8)
        val params = licenseText.parseUrlEncodedParameters()

        val userId = params["u"]
        val name = params["n"]
        val email = params["e"]
        val expirationStr = params["x"]
        val plan = params["p"]

        if (userId == null || name == null || email == null || expirationStr == null) {
            return null
        }

        if (!Regex("^[0-9]{8}$").matches(expirationStr)) {
            return null
        }

        val year = expirationStr.substring(0, 4).toInt()
        val month = expirationStr.substring(4, 6).toInt() - 1
        val day = expirationStr.substring(6, 8).toInt()

        val expiration = Calendar.getInstance().run {
            set(year, month, day)
            time
        }

        return WokwiLicense(userId, name, email, expiration, plan)
    }

    private fun base64DblClickDecode(value: String): String {
        var result = value.replace("_P", "+")
            .replace("_S", "/")
            .replace("=", "")
        while (result.length % 4 > 0) {
            result += "="
        }
        return result
    }

    companion object {
        val LOG = logger<WokwiLicensingService>()
    }


    data class WokwiLicense(
        val userId: String,
        val name: String,
        val email: String,
        val expiration: Date,
        val plan: String?
    ) {
        fun isValid(): Boolean {
            return expiration.after(Date())
        }
    }
}