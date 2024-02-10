package com.github.jozott00.wokwiintellij.utils

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.awt.event.ActionEvent

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

    fun pluginNotifications(): NotificationGroup {
        return NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
    }

}


class NotifyAction(text: String, val action: (AnActionEvent, Notification) -> Unit) : NotificationAction(text) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        action(e, notification)
    }

}
