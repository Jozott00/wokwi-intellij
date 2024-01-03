package com.github.jozott00.wokwiintellij.utils

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object WokwiNotifier {

    private val NOTIFICATION_GROUP = "Wokwi Simulator"

    fun notifyBalloon(message: String, project: Project, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }

}