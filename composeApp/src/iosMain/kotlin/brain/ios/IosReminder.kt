@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package brain.ios

import brain.model.Task
import brain.studio.ReminderGateway
import platform.UserNotifications.*
import kotlin.time.Clock

internal class IosReminder : ReminderGateway {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    override val available: Boolean = true

    override suspend fun notify(task: Task) {
        schedule(task, delaySeconds = 1.0)
    }

    fun schedule(task: Task, delaySeconds: Double? = null) {
        if (task.completed) {
            cancel(task.id)
            return
        }
        requestPermission { granted ->
            if (!granted) return@requestPermission
            cancel(task.id)
            val content = UNMutableNotificationContent().apply {
                setTitle("Kasha · Задача")
                setBody(task.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(180).orEmpty())
                setSound(UNNotificationSound.defaultSound)
            }
            val seconds = delaySeconds ?: ((task.nextReminderAt - Clock.System.now().toEpochMilliseconds()) / 1000.0)
                .coerceAtLeast(1.0)
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = task.id,
                content = content,
                trigger = trigger,
            )
            center.addNotificationRequest(request) { _ -> }
        }
    }

    fun sync(tasks: List<Task>) {
        val activeIds = tasks.filterNot { it.completed }.map { it.id }.toSet()
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val obsolete = requests.orEmpty()
                .map { it.identifier }
                .filter { it.startsWith("kasha-task-") && it.removePrefix("kasha-task-") !in activeIds }
            if (obsolete.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(obsolete)
        }
        tasks.filterNot { it.completed }.forEach(::schedule)
    }

    fun cancel(taskId: String) {
        val ids = listOf(taskId, "kasha-task-$taskId")
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    private fun requestPermission(block: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, error -> block(granted && error == null) }
    }
}
