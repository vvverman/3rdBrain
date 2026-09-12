package brain.studio

import brain.model.Task

/** Платформа показывает локальное уведомление. Сервер или сеть для этого не нужны. */
interface ReminderGateway {
    val available: Boolean
    suspend fun notify(task: Task)
}

object NoopReminderGateway : ReminderGateway {
    override val available: Boolean = false
    override suspend fun notify(task: Task) = Unit
}
