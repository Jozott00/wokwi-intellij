package com.github.jozott00.wokwiintellij.utils

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project

private val pluginNotifications = NotificationGroup.balloonGroup("Rust plugin")

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

    fun notifyBalloon(title: String, message: String, type: NotificationType = NotificationType.INFORMATION) {
        val notification = pluginNotifications().createNotification(title, message, type)
        Notifications.Bus.notify(notification)
    }

    fun pluginNotifications(): NotificationGroup {
        return NotificationGroupManager.getInstance().getNotificationGroup("Wokwi Simulator")
    }

}