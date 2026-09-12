@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package brain.ios

import brain.model.Task
import brain.studio.ReminderGateway
import platform.UserNotifications.*
import kotlin.time.Clock

internal class IosReminder : ReminderGateway {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    override val available: Boolean = true

    /** Уведомление уже ставится системой заранее через sync, повторно показывать его не нужно. */
    override suspend fun notify(task: Task) = Unit

    override suspend fun sync(tasks: List<Task>) {
        val active = tasks.filterNot { it.completed }
        val activeIds = active.map { identifier(it.id) }.toSet()
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val obsolete = requests.orEmpty()
                .map { it.identifier }
                .filter { it.startsWith(PREFIX) && it !in activeIds }
            if (obsolete.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(obsolete)
        }
        active.forEach(::schedule)
    }

    private fun schedule(task: Task) {
        requestPermission { granted ->
            if (!granted) return@requestPermission
            val id = identifier(task.id)
            center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
            val content = UNMutableNotificationContent().apply {
                setTitle("Kasha · Задача")
                setBody(task.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(180).orEmpty())
                setSound(UNNotificationSound.defaultSound)
            }
            val seconds = ((task.nextReminderAt - Clock.System.now().toEpochMilliseconds()) / 1000.0)
                .coerceAtLeast(1.0)
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = id,
                content = content,
                trigger = trigger,
            )
            center.addNotificationRequest(request) { _ -> }
        }
    }

    fun cancel(taskId: String) {
        val id = identifier(taskId)
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
    }

    private fun requestPermission(block: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, error -> block(granted && error == null) }
    }

    private fun identifier(taskId: String) = "$PREFIX$taskId"

    private companion object {
        const val PREFIX = "kasha-task-"
    }
}
