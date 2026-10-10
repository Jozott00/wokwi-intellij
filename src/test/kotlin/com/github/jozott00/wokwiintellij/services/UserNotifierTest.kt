package com.github.jozott00.wokwiintellij.services

import kotlin.test.Test
import kotlin.test.assertEquals

class UserNotifierTest {

    @Test
    fun `info warning and error dispatch expected notification types`() {
        val notifier = RecordingUserNotifier()

        notifier.info("Info", "A")
        notifier.warning("Warning", "B")
        notifier.error("Error", "C")

        assertEquals(
            listOf(
                RecordedNotification("Info", "A", UserNotificationType.INFORMATION),
                RecordedNotification("Warning", "B", UserNotificationType.WARNING),
                RecordedNotification("Error", "C", UserNotificationType.ERROR),
            ),
            notifier.notifications,
        )
    }

    private class RecordingUserNotifier : UserNotifier {
        val notifications = mutableListOf<RecordedNotification>()

        override fun notify(
            title: String,
            message: String,
            type: UserNotificationType,
            action: UserNotificationAction?,
        ) {
            notifications.add(RecordedNotification(title, message, type))
        }
    }

    private data class RecordedNotification(
        val title: String,
        val message: String,
        val type: UserNotificationType,
    )
}
