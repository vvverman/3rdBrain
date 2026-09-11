package brain.desktop

import brain.model.Task
import brain.studio.ReminderGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Локальное системное уведомление macOS. Сеть и внешний сервис не используются.
 * Пока desktop-приложение запущено, StudioState сам выдаёт сюда наступившие задачи.
 */
class DesktopReminder : ReminderGateway {
    override val available: Boolean = System.getProperty("os.name").lowercase().contains("mac")

    override suspend fun notify(task: Task) {
        if (!available) return
        withContext(Dispatchers.IO) {
            val body = task.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(180).orEmpty()
            val safeBody = body.replace("\\", "\\\\").replace("\"", "\\\"")
            val script = "display notification \"$safeBody\" with title \"Kasha · Задача\" sound name \"Glass\""
            val process = ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
        }
    }
}
