package brain.studio

/**
 * Временная source-compatibility для старых внутренних тестов и незавершённых
 * платформенных оболочек. В продуктовом UI эти API больше не используются.
 */
@Deprecated("Заголовок заметки теперь вычисляется из первой строки текста")
fun StudioState.editTitle(value: String) = Unit

@Deprecated("Используйте sendToNotes() или sendToTasks()")
suspend fun StudioState.send() = sendToNotes()

@Deprecated("Отдельного title больше нет")
suspend fun StudioState.saveNote(id: String, title: String, body: String) = saveNote(id, body)

@Deprecated("Контрол скорости воспроизведения удалён из текущего UX")
suspend fun StudioState.changePlaybackRate() = Unit

@Deprecated("Переключатель Note/Task на экране проекта удалён")
enum class DestinationKind { NOTE, TASK }

@Deprecated("Направление выбирается отдельной кнопкой до экрана проекта")
val StudioState.destinationKind: DestinationKind get() = DestinationKind.NOTE

@Deprecated("Направление выбирается отдельной кнопкой до экрана проекта")
fun StudioState.chooseDestinationKind(value: DestinationKind) = Unit
