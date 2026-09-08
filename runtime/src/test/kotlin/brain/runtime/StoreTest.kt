package brain.runtime

import brain.model.CaptureDraftUpdate
import brain.model.CaptureStatus
import brain.model.DistributionRequest
import brain.model.ProjectDraft
import brain.model.RuntimeStatus
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreTest {
    @Test
    fun appendIsIdempotentAndOldTextStaysUntouched() = runBlocking {
        val dir = Files.createTempDirectory("thirdbrain-test")
        try {
            val store = FileBrainStore(dir) { RuntimeStatus() }
            val project = store.createProject(ProjectDraft("Проект", instruction = "Тест"))
            val first = store.createCapture("a.webm", byteArrayOf(1, 2, 3))
            store.updateCapture(first.id) { it.copy(status = CaptureStatus.NEEDS_MODEL) }
            store.updateDraft(first.id, CaptureDraftUpdate("Первая", "Старый текст"))
            val note = store.distribute(first.id, DistributionRequest(project.id))

            val second = store.createCapture("b.webm", byteArrayOf(4, 5, 6))
            store.updateCapture(second.id) { it.copy(status = CaptureStatus.NEEDS_MODEL) }
            store.updateDraft(second.id, CaptureDraftUpdate("Вторая", "Новая мысль"))
            val appended = store.distribute(second.id, DistributionRequest(project.id, note.id))
            val repeated = store.distribute(second.id, DistributionRequest(project.id, note.id))

            assertEquals("Старый текст\n\nНовая мысль", appended.body)
            assertEquals(appended, repeated)
            assertTrue(appended.body.startsWith("Старый текст"))
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test
    fun stateSurvivesReopen() = runBlocking {
        val dir = Files.createTempDirectory("thirdbrain-reopen")
        try {
            val first = FileBrainStore(dir) { RuntimeStatus() }
            first.createProject(ProjectDraft("Живой проект"))
            val second = FileBrainStore(dir) { RuntimeStatus() }
            assertEquals(listOf("Живой проект"), second.snapshot().projects.map { it.title })
        } finally { dir.toFile().deleteRecursively() }
    }
}
