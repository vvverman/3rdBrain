package brain.runtime

import brain.domain.NoteText
import brain.model.Capture
import brain.model.CaptureStatus
import brain.model.Project
import brain.model.RuntimeStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class LocalProcessing(private val store: FileBrainStore, private val env: Map<String, String> = System.getenv()) {
    private val whisperCli = env["THIRDBRAIN_WHISPER_CLI"]
    private val whisperModel = env["THIRDBRAIN_WHISPER_MODEL"]
    private val llamaCli = env["THIRDBRAIN_LLAMA_CLI"]
    private val llamaModel = env["THIRDBRAIN_LLAMA_MODEL"]
    private val ffmpeg = env["THIRDBRAIN_FFMPEG"] ?: "ffmpeg"

    fun status(): RuntimeStatus = RuntimeStatus(
        whisperConfigured = !whisperCli.isNullOrBlank() && !whisperModel.isNullOrBlank(),
        llmConfigured = !llamaCli.isNullOrBlank() && !llamaModel.isNullOrBlank(),
        localOnly = true,
        message = when {
            whisperCli.isNullOrBlank() || whisperModel.isNullOrBlank() -> "Настройте whisper.cpp, чтобы получить локальную транскрипцию. Аудио уже сохраняется локально."
            llamaCli.isNullOrBlank() || llamaModel.isNullOrBlank() -> "Whisper настроен. Для оформления текста и сортировки проектов добавьте локальную llama.cpp модель."
            else -> "Whisper и локальная LLM настроены."
        },
    )

    suspend fun process(captureId: String): Capture {
        val original = store.capture(captureId) ?: error("Запись не найдена")
        if (!status().whisperConfigured) {
            return store.updateCapture(captureId) { it.copy(status = CaptureStatus.NEEDS_MODEL, message = status().message) }
        }
        return try {
            store.updateCapture(captureId) { it.copy(status = CaptureStatus.TRANSCRIBING, message = "") }
            val transcript = transcribe(original)
            val titleFallback = NoteText.title(transcript)
            val projects = store.snapshot().projects
            if (!status().llmConfigured) {
                store.updateCapture(captureId) {
                    it.copy(transcript = transcript, preparedText = transcript, title = titleFallback, status = CaptureStatus.READY, message = status().message)
                }
            } else {
                store.updateCapture(captureId) { it.copy(transcript = transcript, status = CaptureStatus.POLISHING) }
                val polished = polish(transcript)
                val relevance = rankProjects(polished.text, projects)
                store.updateCapture(captureId) {
                    it.copy(transcript = transcript, preparedText = polished.text, title = polished.title.ifBlank { titleFallback }, relevance = relevance, status = CaptureStatus.READY, message = "")
                }
            }
        } catch (e: Exception) {
            store.updateCapture(captureId) { it.copy(status = CaptureStatus.FAILED, message = e.message ?: "Ошибка локальной обработки") }
        }
    }

    private fun transcribe(capture: Capture): String {
        val source = store.resolveAudio(capture)
        val work = store.root.resolve("work").resolve(capture.id).also { it.createDirectories() }
        val wav = work.resolve("input.wav")
        val outBase = work.resolve("transcript")
        try {
            runCommand(listOf(ffmpeg, "-y", "-i", source.toString(), "-ar", "16000", "-ac", "1", wav.toString()), "ffmpeg")
            runCommand(listOf(whisperCli!!, "-m", whisperModel!!, "-f", wav.toString(), "-l", "ru", "-otxt", "-of", outBase.toString()), "Whisper")
            val txt = Path.of(outBase.toString() + ".txt")
            require(txt.exists()) { "Whisper не создал транскрипт" }
            return Files.readString(txt).trim().also { require(it.isNotEmpty()) { "Речь не распознана" } }
        } finally {
            runCatching { work.toFile().deleteRecursively() }
        }
    }

    private data class Polished(val title: String, val text: String)

    private fun polish(transcript: String): Polished {
        val prompt = """
Ты редактор русских голосовых заметок. Не добавляй факты и не меняй смысл, отрицания, числа, имена и решения.
Убери только речевой мусор и случайные повторы, расставь пунктуацию, разбей на абзацы.
Ответ строго в формате:
TITLE: короткий заголовок
TEXT:
полный аккуратный текст

ТРАНСКРИПТ:
$transcript
        """.trimIndent()
        val output = llama(prompt, 1200)
        val title = output.lineSequence().firstOrNull { it.startsWith("TITLE:") }?.removePrefix("TITLE:")?.trim().orEmpty()
        val text = output.substringAfter("TEXT:", output).trim()
        require(text.isNotBlank()) { "Локальная LLM вернула пустой текст" }
        return Polished(title, text)
    }

    private fun rankProjects(text: String, projects: List<Project>): Map<String, Int> {
        if (projects.isEmpty()) return emptyMap()
        val catalog = projects.joinToString("\n") { "${it.id}\t${it.title}\t${it.description}\t${it.instruction}" }
        val prompt = """
Оцени, насколько русская заметка относится к каждому проекту. Верни только строки вида ID<TAB>ЧИСЛО от 0 до 100. Все проекты должны присутствовать ровно один раз.

ЗАМЕТКА:
$text

ПРОЕКТЫ:
$catalog
        """.trimIndent()
        val output = llama(prompt, 500)
        val validIds = projects.mapTo(hashSetOf()) { it.id }
        return output.lineSequence().mapNotNull { line ->
            val parts = line.trim().split('\t')
            if (parts.size < 2 || parts[0] !in validIds) null
            else parts[1].trim().toIntOrNull()?.coerceIn(0, 100)?.let { parts[0] to it }
        }.toMap()
    }

    private fun llama(prompt: String, maxTokens: Int): String = runCommand(
        listOf(llamaCli!!, "-m", llamaModel!!, "-p", prompt, "-n", maxTokens.toString(), "--temp", "0.1", "--no-display-prompt"),
        "llama.cpp",
    ).trim()

    private fun runCommand(command: List<String>, name: String): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        require(code == 0) { "$name завершился с кодом $code: ${output.takeLast(2000)}" }
        return output
    }
}
