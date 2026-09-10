package brain.domain

import brain.model.*
import kotlinx.serialization.Serializable

/** Правила хранения общие для браузера, будущих мобильных и настольных клиентов. */
@Serializable
data class BrainData(
    val projects: List<Project> = emptyList(),
    val notes: List<Note> = emptyList(),
    val captures: List<Capture> = emptyList(),
) {
    fun addProject(id: String, now: Long, draft: ProjectDraft): BrainData {
        require(projects.none { it.id == id }) { "Повторный идентификатор проекта" }
        require(draft.title.isNotBlank()) { "Введите название проекта" }
        return copy(projects = projects + Project(id, draft.title.trim(), draft.description.trim(), draft.instruction.trim(), createdAt = now))
    }

    fun updateProject(id: String, update: ProjectUpdate): BrainData {
        require(update.title.isNotBlank()) { "Введите название проекта" }
        val project = projects.firstOrNull { it.id == id } ?: error("Проект не найден")
        val changed = project.copy(title = update.title.trim(), description = update.description.trim(), instruction = update.instruction.trim())
        return copy(projects = projects.map { if (it.id == id) changed else it })
    }

    fun pinProject(id: String, pinned: Boolean): BrainData {
        val old = projects.firstOrNull { it.id == id } ?: error("Проект не найден")
        if (old.pinned == pinned) return this
        val order = if (pinned) (projects.filter { it.pinned }.maxOfOrNull { it.pinOrder } ?: -1) + 1 else old.pinOrder
        return copy(projects = projects.map { if (it.id == id) old.copy(pinned = pinned, pinOrder = order) else it })
    }

    fun orderPins(ids: List<String>): BrainData {
        val pinned = projects.filter { it.pinned }.map { it.id }.toSet()
        require(ids.size == pinned.size && ids.toSet() == pinned) { "Порядок должен включать все закреплённые проекты ровно один раз" }
        val orders = ids.withIndex().associate { it.value to it.index }
        return copy(projects = projects.map { p -> orders[p.id]?.let { p.copy(pinOrder = it) } ?: p })
    }

    fun updateNote(id: String, update: NoteUpdate, now: Long): BrainData {
        val old = notes.firstOrNull { it.id == id } ?: error("Заметка не найдена")
        val changed = old.copy(title = update.title.trim().ifBlank { "Без названия" }, body = update.body, updatedAt = now)
        return copy(notes = notes.map { if (it.id == id) changed else it })
    }

    fun pinNote(id: String, pinned: Boolean): BrainData {
        val old = notes.firstOrNull { it.id == id } ?: error("Заметка не найдена")
        if (old.pinned == pinned) return this
        val order = if (pinned) {
            (notes.filter { it.projectId == old.projectId && it.pinned }.maxOfOrNull { it.pinOrder } ?: -1) + 1
        } else old.pinOrder
        val changed = old.copy(pinned = pinned, pinOrder = order)
        return copy(notes = notes.map { if (it.id == id) changed else it })
    }

    fun addCapture(capture: Capture): BrainData {
        require(captures.none { it.id == capture.id }) { "Повторный идентификатор записи" }
        return copy(captures = captures + capture)
    }

    fun updateCapture(id: String, transform: (Capture) -> Capture): BrainData {
        val old = captures.firstOrNull { it.id == id } ?: error("Запись не найдена")
        val changed = transform(old)
        require(changed.id == old.id && changed.createdAt == old.createdAt && changed.audioFileName == old.audioFileName) { "Нельзя подменить источник записи" }
        return copy(captures = captures.map { if (it.id == id) changed else it })
    }

    fun updateDraft(id: String, update: CaptureDraftUpdate): BrainData = updateCapture(id) { old ->
        require(!old.status.isWorking && old.noteId == null) { "Дождитесь обработки. Сохранённый источник изменять нельзя" }
        old.copy(title = update.title.trim().ifBlank { NoteText.title(update.text) }, preparedText = update.text, draftEdited = true,
            relevance = emptyMap(), rankingApplied = false)
    }

    fun distribute(id: String, request: DistributionRequest, newNoteId: String, now: Long): Pair<BrainData, Note> {
        val capture = captures.firstOrNull { it.id == id } ?: error("Запись не найдена")
        capture.noteId?.let { existing -> return this to (notes.firstOrNull { it.id == existing } ?: error("Заметка источника не найдена")) }
        require(!capture.status.isWorking) { "Дождитесь завершения обработки" }
        val project = projects.firstOrNull { it.id == request.projectId } ?: error("Проект не найден")
        val addition = capture.textToSave
        require(addition.isNotBlank()) { "В записи пока нет текста" }
        val note = if (request.noteId == null) {
            require(notes.none { it.id == newNoteId }) { "Повторный идентификатор заметки" }
            Note(newNoteId, project.id, request.title?.trim()?.takeIf { it.isNotEmpty() } ?: capture.title,
                addition, now, now)
        } else {
            val old = notes.firstOrNull { it.id == request.noteId } ?: error("Заметка не найдена")
            require(old.projectId == project.id) { "Заметка относится к другому проекту" }
            old.copy(body = NoteText.append(old.body, addition), updatedAt = now)
        }
        val updated = if (request.noteId == null) notes + note else notes.map { if (it.id == note.id) note else it }
        return copy(notes = updated, captures = captures.map { if (it.id == id) it.copy(noteId = note.id, appendedAt = now) else it }) to note
    }
}
