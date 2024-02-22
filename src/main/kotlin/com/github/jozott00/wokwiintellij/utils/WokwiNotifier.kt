package com.github.jozott00.wokwiintellij.utils

import com.github.jozott00.wokwiintellij.exceptions.GenericError
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WokwiNotifier {

    private val NOTIFICATION_GROUP = "Wokwi Simulator"


    fun notifyBalloon(
        title: String,
        message: String = "",
        type: NotificationType = NotificationType.INFORMATION,
        action: NotifyAction? = null
    ) {
        val notification = pluginNotifications().createNotification(title, message, type)
        action?.let { notification.addAction(it) }
        Notifications.Bus.notify(notification)
    }

    suspend fun notifyBalloonAsync(error: GenericError, action: NotifyAction? = null) {
        notifyBalloonAsync(error.title, error.message, NotificationType.ERROR, action)
    }

    suspend fun notifyBalloonAsync(
        title: String,
        message: String = "",
        type: NotificationType = NotificationType.INFORMATION,
        action: NotifyAction? = null
    ) {
        withContext(Dispatchers.EDT) {
            val notification = pluginNotifications().createNotification(title, message, type)
            action?.let { notification.addAction(it) }
            Notifications.Bus.notify(notification)
        }
    }

    fun pluginNotifications(): NotificationGroup {
        return NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
    }

}


class NotifyAction(text: String, val action: (AnActionEvent, Notification) -> Unit) : NotificationAction(text) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        action(e, notification)
    }

}
