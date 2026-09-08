package brain.domain

import brain.model.Capture
import brain.model.Note
import brain.model.Project

object ProjectOrder {
    fun sorted(projects: List<Project>, scores: Map<String, Int> = emptyMap()): List<Project> =
        projects.sortedWith(
            compareByDescending<Project> { it.pinned }
                .thenComparator { a, b ->
                    if (a.pinned && b.pinned && a.pinOrder != b.pinOrder) a.pinOrder.compareTo(b.pinOrder)
                    else if (!a.pinned && !b.pinned && (scores[a.id] ?: 0) != (scores[b.id] ?: 0)) (scores[b.id] ?: 0).compareTo(scores[a.id] ?: 0)
                    else a.title.lowercase().compareTo(b.title.lowercase())
                }
                .thenBy { it.id }
        )
}

object NoteText {
    fun append(existing: String, addition: String): String {
        val clean = addition.trim()
        if (clean.isEmpty()) return existing
        return if (existing.isBlank()) clean else existing.trimEnd() + "\n\n" + clean
    }

    fun title(text: String): String = text
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        ?.take(70)
        ?: "Новая заметка"
}

object SnapshotQueries {
    fun inbox(captures: List<Capture>): List<Capture> = captures.filter { it.isInbox }.sortedByDescending { it.createdAt }
    fun notes(projectId: String, notes: List<Note>): List<Note> = notes.filter { it.projectId == projectId }.sortedByDescending { it.updatedAt }
}
