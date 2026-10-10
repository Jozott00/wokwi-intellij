package com.github.jozott00.wokwiintellij.ide.services

import com.github.jozott00.wokwiintellij.services.UserNotificationAction
import com.github.jozott00.wokwiintellij.services.UserNotificationType
import com.github.jozott00.wokwiintellij.services.UserNotifier
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager

/**
 * IntelliJ notification-group backed implementation of [UserNotifier].
 */
object IntelliJUserNotifier : UserNotifier {
    private const val NOTIFICATION_GROUP = "Wokwi Simulator"

    override fun notify(
        title: String,
        message: String,
        type: UserNotificationType,
        action: UserNotificationAction?,
    ) {
        ApplicationManager.getApplication().invokeLater {
            val notification = pluginNotifications().createNotification(title, message, type.toIntelliJType())
            action?.let { notification.addAction(it.toNotificationAction()) }
            Notifications.Bus.notify(notification)
        }
    }

    private fun UserNotificationType.toIntelliJType(): NotificationType =
        when (this) {
            UserNotificationType.INFORMATION -> NotificationType.INFORMATION
            UserNotificationType.WARNING -> NotificationType.WARNING
            UserNotificationType.ERROR -> NotificationType.ERROR
        }

    private fun UserNotificationAction.toNotificationAction(): NotificationAction =
        object : NotificationAction(text) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                this@toNotificationAction.run()
            }
        }

    private fun pluginNotifications(): NotificationGroup =
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
}
