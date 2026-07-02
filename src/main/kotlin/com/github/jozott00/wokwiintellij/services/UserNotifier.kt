package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.exceptions.GenericError

/**
 * Service-level user notification boundary.
 *
 * Implementations own platform-specific notification APIs and threading. Callers should report intent through this
 * contract instead of depending on IntelliJ notification classes directly.
 */
interface UserNotifier {
    fun notify(
        title: String,
        message: String = "",
        type: UserNotificationType = UserNotificationType.INFORMATION,
        action: UserNotificationAction? = null,
    )

    fun info(
        title: String,
        message: String = "",
        action: UserNotificationAction? = null,
    ) = notify(title, message, UserNotificationType.INFORMATION, action)

    fun warning(
        title: String,
        message: String = "",
        action: UserNotificationAction? = null,
    ) = notify(title, message, UserNotificationType.WARNING, action)

    fun error(
        title: String,
        message: String = "",
        action: UserNotificationAction? = null,
    ) = notify(title, message, UserNotificationType.ERROR, action)

    fun error(
        error: GenericError,
        action: UserNotificationAction? = null,
    ) = error(error.title, error.message, action)
}

enum class UserNotificationType {
    INFORMATION,
    WARNING,
    ERROR,
}

data class UserNotificationAction(
    val text: String,
    val run: () -> Unit,
)
