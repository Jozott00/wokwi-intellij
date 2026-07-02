package com.github.jozott00.wokwiintellij.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun testWokwiLicensingService(
    initialLicense: String?,
): WokwiLicensingService {
    var storedLicense = initialLicense
    return WokwiLicensingService(
        cs = CoroutineScope(Dispatchers.Unconfined),
        readStoredLicense = { storedLicense },
        writeStoredLicense = { storedLicense = it },
        userNotifier = NoOpUserNotifier,
    )
}

private object NoOpUserNotifier : UserNotifier {
    override fun notify(
        title: String,
        message: String,
        type: UserNotificationType,
        action: UserNotificationAction?,
    ) = Unit
}
