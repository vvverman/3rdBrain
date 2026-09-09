package brain.desktop

import brain.domain.RecorderGateway
import brain.model.Capture
import brain.runtime.FileBrainStore
import kotlinx.coroutines.*
import java.nio.file.*
import java.util.UUID
import javax.sound.sampled.*
import kotlin.concurrent.thread

class DesktopRecorder(private val root: Path, private val store: FileBrainStore, private val enqueue: suspend (String) -> Unit) : RecorderGateway, AutoCloseable {
    private val pending = root.resolve("pending")
    private val consentFile = root.resolve("microphone-consent")
    @Volatile private var currentPhase = "idle"
    @Volatile private var running = false
    private var input: TargetDataLine? = null
    private var worker: Thread? = null
    @Volatile private var failure: Throwable? = null
    init { Files.createDirectories(pending) }
    private fun journals(): List<Path> = Files.list(pending).use { files -> files.filter { it.fileName.toString().endsWith(".wav") }.sorted().toList() }
    override suspend fun hasConsent(): Boolean = withContext(Dispatchers.IO) { Files.exists(consentFile) }
    override suspend fun hasPending(): Boolean = withContext(Dispatchers.IO) { currentPhase == "idle" && journals().isNotEmpty() }
    override fun phase(): String = currentPhase

    override suspend fun start() = withContext(Dispatchers.IO) {
        check(currentPhase == "idle") { "Запись уже идёт" }
        check(journals().isEmpty()) { "Сначала сохраните предыдущую запись" }
        var line: TargetDataLine? = null
        for (rate in listOf(16000, 44100, 48000)) {
            val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
            try {
                line = AudioSystem.getTargetDataLine(format)
                line.open(format); break
            } catch (_: LineUnavailableException) { line?.close(); line = null }
            catch (_: IllegalArgumentException) { line?.close(); line = null }
            catch (e: SecurityException) { line?.close(); error("Разрешите 3rdBrain доступ к микрофону в Системных настройках → Конфиденциальность и безопасность → Микрофон.") }
        }
        val actual = line ?: error("Микрофон недоступен. Проверьте разрешение 3rdBrain и выбранное устройство ввода в настройках macOS.")
        var journal: WavJournal? = null
        try {
            journal = WavJournal(pending.resolve("${UUID.randomUUID()}.wav"), actual.format.sampleRate.toInt())
            val writer = journal
            failure = null; input = actual; running = true; actual.start(); currentPhase = "recording"
            Files.writeString(consentFile, "Разрешён пользователем")
            worker = thread(name = "3rdBrain-microphone", isDaemon = true) {
                try {
                    val buffer = ByteArray(4096); var syncedAt = System.nanoTime()
                    while (running) {
                        if (currentPhase == "paused") { Thread.sleep(25); continue }
                        val count = actual.read(buffer, 0, buffer.size)
                        if (count > 0) writer.append(buffer, count)
                        if (System.nanoTime() - syncedAt > 1_000_000_000) { writer.flush(); syncedAt = System.nanoTime() }
                    }
                } catch (e: Exception) { if (running) failure = e }
                finally { running = false; actual.close(); runCatching { writer.close() }; currentPhase = "idle" }
            }
        } catch (e: Exception) { running = false; currentPhase = "idle"; actual.close(); runCatching { journal?.close() }; throw e }
    }
    override suspend fun pause() = withContext(Dispatchers.IO) {
        check(currentPhase == "recording") { "Запись не активна" }
        currentPhase = "paused"; input?.stop(); input?.flush()
    }
    override suspend fun resume() = withContext(Dispatchers.IO) {
        check(currentPhase == "paused") { "Запись не на паузе" }
        input?.flush(); input?.start(); currentPhase = "recording"
    }
    override suspend fun stopAndUpload(): Capture = withContext(Dispatchers.IO) {
        close()
        recoverPending()
    }
    override suspend fun recoverPending(): Capture = withContext(Dispatchers.IO) {
        check(currentPhase == "idle") { "Сначала остановите микрофон" }
        val source = journals().firstOrNull() ?: error("Нет записи для восстановления")
        val size = WavJournal.repair(source)
        if (size == 0L) {
            Files.delete(source) // Только пустой заголовок, ни одного аудиосэмпла.
            error("Микрофон не успел записать звук. Можно начать новую запись.")
        }
        val id = source.fileName.toString().removeSuffix(".wav")
        val capture = store.createCapture("original.wav", Files.readAllBytes(source), id)
        // Удаляется только после успешного дискового commit. Повтор по id не дублирует запись.
        Files.delete(source)
        enqueue(capture.id)
        capture
    }
    override fun close() {
        running = false; input?.stop(); input?.close()
        worker?.join(3000)
        check(worker?.isAlive != true) { "Микрофон завершает запись. Повторите сохранение." }
        worker = null; input = null; currentPhase = "idle"
    }
}
