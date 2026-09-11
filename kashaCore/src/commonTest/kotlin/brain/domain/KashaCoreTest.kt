package brain.domain

import brain.model.*
import brain.studio.Intelligence
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class KashaCoreTest {
    @Test
    fun manualProjectOrderSurvivesOtherSortModes() {
        var data = BrainData()
            .addProject("a", 100, ProjectDraft("Бета"))
            .addProject("b", 200, ProjectDraft("Альфа"))
            .addProject("c", 300, ProjectDraft("Гамма"))
        data = data.orderProjects(listOf("c", "a", "b"))

        assertEquals(listOf("c", "a", "b"), UserSort.projects(data.projects, SortMode.MANUAL).map { it.id })
        assertEquals(listOf("b", "a", "c"), UserSort.projects(data.projects, SortMode.ALPHABETICAL).map { it.id })
        assertEquals(listOf("c", "a", "b"), UserSort.projects(data.projects, SortMode.MANUAL).map { it.id })
    }

    @Test
    fun notesAndTasksUseSameFourSortModes() {
        val notes = listOf(
            Note("n1", "p", "Бета", "", 100, 300, manualOrder = 1),
            Note("n2", "p", "Альфа", "", 300, 100, manualOrder = 0),
        )
        assertEquals(listOf("n2", "n1"), UserSort.notes(notes, SortMode.ALPHABETICAL).map { it.id })
        assertEquals(listOf("n2", "n1"), UserSort.notes(notes, SortMode.CREATED).map { it.id })
        assertEquals(listOf("n1", "n2"), UserSort.notes(notes, SortMode.UPDATED).map { it.id })
        assertEquals(listOf("n2", "n1"), UserSort.notes(notes, SortMode.MANUAL).map { it.id })

        val tasks = listOf(
            Task("t1", "p", "Бета", 100, 300, 1),
            Task("t2", "p", "Альфа", 300, 100, 0),
        )
        assertEquals(listOf("t2", "t1"), UserSort.tasks(tasks, SortMode.ALPHABETICAL).map { it.id })
        assertEquals(listOf("t2", "t1"), UserSort.tasks(tasks, SortMode.CREATED).map { it.id })
        assertEquals(listOf("t1", "t2"), UserSort.tasks(tasks, SortMode.UPDATED).map { it.id })
        assertEquals(listOf("t2", "t1"), UserSort.tasks(tasks, SortMode.MANUAL).map { it.id })
    }

    @Test
    fun voiceCaptureCanBecomeTaskOnlyOnce() {
        val project = Project("p", "Проект", createdAt = 1, updatedAt = 1)
        val capture = Capture(
            id = "c",
            createdAt = 2,
            title = "Мысль",
            transcript = "Сделать локальную задачу",
            preparedText = "Сделать локальную задачу",
            status = CaptureStatus.READY,
        )
        val start = BrainData(projects = listOf(project), captures = listOf(capture))
        val (after, task) = start.distributeTask("c", TaskDistributionRequest("p"), "t", 3)

        assertEquals("Сделать локальную задачу", task.text)
        assertEquals("p", task.projectId)
        assertEquals("t", after.captures.single().taskId)
        assertFalse(after.captures.single().isInbox)
        assertFails { after.distribute("c", DistributionRequest("p"), "n", 4) }
    }

    @Test
    fun captureAiWorkflowIsPlatformIndependent() = runTest {
        val intelligence = object : Intelligence {
            override val simulated = false
            override suspend fun transcribe(file: String, language: String, example: String) = "транскрипт"
            override suspend fun title(text: String, language: String) = "Сырой текст — локальный заголовок"
            override suspend fun tidy(text: String, language: String) = "Сырой текст, аккуратно оформленный."
            override suspend fun rank(text: String, projects: List<Project>, language: String) = projects.associate { it.id to 4 }
        }
        val workflow = CaptureWorkflow(intelligence)
        val projects = listOf(Project("p", "Kasha", createdAt = 1, updatedAt = 1))
        val capture = Capture(
            id = "c",
            createdAt = 2,
            transcript = "сырой текст",
            preparedText = "сырой текст",
            status = CaptureStatus.COMPACTING,
        )

        val finished = workflow.finish(capture, projects, "ru")
        assertEquals(CaptureStatus.READY, finished.status)
        assertEquals("Сырой текст — локальный заголовок", finished.title)
        assertEquals(mapOf("p" to 4), finished.relevance)
        assertTrue(finished.rankingApplied)

        val tidied = workflow.tidy(finished, "ru")
        assertEquals("Сырой текст, аккуратно оформленный.", tidied.preparedText)
        assertTrue(tidied.draftEdited)
        assertTrue(tidied.llmApplied)
        assertFalse(tidied.rankingApplied)
        assertEquals(emptyMap(), tidied.relevance)

        val reranked = workflow.rank(tidied, projects, "ru")
        assertEquals(mapOf("p" to 4), reranked.relevance)
        assertTrue(reranked.rankingApplied)
    }

    @Test
    fun coreRejectsUnrelatedTitlesAndInvalidRelevance() = runTest {
        val projects = listOf(Project("p", "Kasha", createdAt = 1, updatedAt = 1))
        val capture = Capture(
            id = "c",
            createdAt = 2,
            transcript = "Проверить сохранение локальной заметки",
            preparedText = "Проверить сохранение локальной заметки",
            status = CaptureStatus.COMPACTING,
        )
        val unrelatedTitle = object : Intelligence {
            override val simulated = false
            override suspend fun transcribe(file: String, language: String, example: String) = ""
            override suspend fun title(text: String, language: String) = "Рецепт шоколадного торта"
            override suspend fun tidy(text: String, language: String) = text
            override suspend fun rank(text: String, projects: List<Project>, language: String) = projects.associate { it.id to 4 }
        }
        assertEquals(
            "Проверить сохранение локальной заметки",
            CaptureWorkflow(unrelatedTitle).finish(capture, projects, "ru").title,
        )

        val invalidRank = object : Intelligence {
            override val simulated = false
            override suspend fun transcribe(file: String, language: String, example: String) = ""
            override suspend fun title(text: String, language: String) = "Локальная заметка"
            override suspend fun tidy(text: String, language: String) = text
            override suspend fun rank(text: String, projects: List<Project>, language: String) = mapOf("p" to 9)
        }
        try {
            CaptureWorkflow(invalidRank).finish(capture, projects, "ru")
            fail("Core обязан отклонить relevance вне диапазона 0..4")
        } catch (_: IllegalArgumentException) {
            // Ожидаемая защита контракта Intelligence.
        }
    }

    @Test
    fun destructiveAiEditIsRejectedInsideCore() = runTest {
        val intelligence = object : Intelligence {
            override val simulated = false
            override suspend fun transcribe(file: String, language: String, example: String) = ""
            override suspend fun title(text: String, language: String) = ""
            override suspend fun tidy(text: String, language: String) = "Совсем другой текст 99"
            override suspend fun rank(text: String, projects: List<Project>, language: String) = emptyMap<String, Int>()
        }
        val original = "Нельзя удалить 42 важных пункта проекта"
        val capture = Capture(
            id = "c",
            createdAt = 1,
            title = "Проект",
            transcript = original,
            preparedText = original,
            status = CaptureStatus.READY,
        )

        try {
            CaptureWorkflow(intelligence).tidy(capture, "ru")
            fail("Core обязан отклонить разрушительную AI-правку")
        } catch (_: IllegalArgumentException) {
            // Ожидаемое поведение: число, отрицание и содержание не сохранились.
        }
    }
}
