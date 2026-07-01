package com.github.jozott00.wokwiintellij.services

import arrow.core.Either
import com.github.jozott00.wokwiintellij.exceptions.GenericError
import kotlinx.coroutines.Job
import java.util.Date

/**
 * High-level license operations used by simulator startup and licensing UI.
 *
 * Implementations own storage, caching, parsing, and validation details. Callers should depend on this service-level
 * contract instead of raw license persistence.
 */
interface LicenseService {
    suspend fun getLicense(): String?

    fun updateLicense(license: String): Job

    fun removeLicense(): Job

    suspend fun loadAndCheckLicense(): Either<GenericError, String>

    fun parseLicense(license: String): WokwiLicense?
}

data class WokwiLicense(
    val userId: String,
    val name: String,
    val email: String,
    val expiration: Date,
    val plan: String?,
) {
    fun isValid(): Boolean = expiration.after(Date())
}
