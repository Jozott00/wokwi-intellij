package com.github.jozott00.wokwiintellij.utils

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.awt.event.ActionEvent

object WokwiNotifier {

    private val NOTIFICATION_GROUP = "Wokwi Simulator"

    fun notifyBalloon(message: String, project: Project, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    fun notifyBalloon(message: String, type: NotificationType = NotificationType.INFORMATION) {
        val notification = pluginNotifications().createNotification(message, type)
        Notifications.Bus.notify(notification)
    }


    fun notifyBalloon(
        title: String,
        message: String,
        type: NotificationType = NotificationType.INFORMATION,
        action: NotifyAction? = null
    ) {
        val notification = pluginNotifications().createNotification(title, message, type)
        action?.let { notification.addAction(it) }
        Notifications.Bus.notify(notification)
    }

    fun pluginNotifications(): NotificationGroup {
        return NotificationGroupManager.getInstance().getNotificationGroup("Wokwi Simulator")
    }

}


class NotifyAction(text: String, val action: (AnActionEvent, Notification) -> Unit) : NotificationAction(text) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        action(e, notification)
    }

}
