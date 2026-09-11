package brain.domain

import brain.model.*

object ProjectOrder {
    /** При выборе назначения релевантность важнее пользовательской сортировки. */
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

/** Одни и те же правила сортировки используются на всех платформах. */
object UserSort {
    private fun Project.modified() = if (updatedAt > 0) updatedAt else createdAt

    fun projects(items: List<Project>, mode: SortMode): List<Project> = items.sortedWith(
        compareByDescending<Project> { it.pinned }
            .thenComparator { a, b ->
                if (a.pinned && b.pinned && a.pinOrder != b.pinOrder) return@thenComparator a.pinOrder.compareTo(b.pinOrder)
                when (mode) {
                    SortMode.ALPHABETICAL -> a.title.lowercase().compareTo(b.title.lowercase())
                    SortMode.CREATED -> b.createdAt.compareTo(a.createdAt)
                    SortMode.UPDATED -> b.modified().compareTo(a.modified())
                    SortMode.MANUAL -> a.manualOrder.compareTo(b.manualOrder)
                }
            }
            .thenBy { it.id }
    )

    fun notes(items: List<Note>, mode: SortMode): List<Note> = items.sortedWith(
        compareByDescending<Note> { it.pinned }
            .thenComparator { a, b ->
                if (a.pinned && b.pinned && a.pinOrder != b.pinOrder) return@thenComparator a.pinOrder.compareTo(b.pinOrder)
                when (mode) {
                    SortMode.ALPHABETICAL -> a.title.lowercase().compareTo(b.title.lowercase())
                    SortMode.CREATED -> b.createdAt.compareTo(a.createdAt)
                    SortMode.UPDATED -> b.updatedAt.compareTo(a.updatedAt)
                    SortMode.MANUAL -> a.manualOrder.compareTo(b.manualOrder)
                }
            }
            .thenBy { it.id }
    )

    fun tasks(items: List<Task>, mode: SortMode): List<Task> = items.sortedWith(
        Comparator { a, b ->
            val result = when (mode) {
                SortMode.ALPHABETICAL -> a.text.lowercase().compareTo(b.text.lowercase())
                SortMode.CREATED -> b.createdAt.compareTo(a.createdAt)
                SortMode.UPDATED -> b.updatedAt.compareTo(a.updatedAt)
                SortMode.MANUAL -> a.manualOrder.compareTo(b.manualOrder)
            }
            if (result != 0) result else a.id.compareTo(b.id)
        }
    )
}

object NoteText {
    fun append(existing: String, addition: String): String {
        val clean = addition.trim()
        if (clean.isEmpty()) return existing
        return if (existing.isEmpty()) addition else existing + "\n\n" + addition
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
    fun notes(projectId: String, notes: List<Note>, mode: SortMode = SortMode.UPDATED): List<Note> = UserSort.notes(notes.filter { it.projectId == projectId }, mode)
    fun tasks(tasks: List<Task>, mode: SortMode = SortMode.UPDATED): List<Task> = UserSort.tasks(tasks, mode)
}
