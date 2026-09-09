package brain.desktop

import brain.domain.*
import brain.model.*
import brain.runtime.*
import kotlinx.coroutines.*
import java.nio.channels.FileChannel
import java.nio.file.*

/** Только платформенные порты: UI, модели данных и правила остаются общими. */
class DesktopServices(val root: Path, val resources: Path) : AutoCloseable {
    private val lockChannel: FileChannel
    private val lock: java.nio.channels.FileLock
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store: FileBrainStore
    val processing: LocalProcessing
    val recorder: DesktopRecorder
    val audio: DesktopAudio
    val repository: BrainRepository

    init {
        Files.createDirectories(root)
        lockChannel = FileChannel.open(root.resolve(".desktop.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        lock = try { lockChannel.tryLock() ?: error("3rdBrain уже запущен. Откройте его окно в Dock.") }
        catch (e: Exception) { lockChannel.close(); throw e }
        try {
            val env = bundledEnvironment(resources)
            var processor: LocalProcessing? = null
            store = FileBrainStore(root) { processor?.status() ?: RuntimeStatus() }
            processing = LocalProcessing(store, env).also { processor = it }
            repository = DesktopRepository(store, processing, scope)
            recorder = DesktopRecorder(root, store) { id -> processing.enqueue(id, scope) }
            audio = DesktopAudio(store, env.getValue("THIRDBRAIN_FFMPEG"), root, scope)
        } catch (e: Exception) { lock.release(); lockChannel.close(); scope.cancel(); throw e }
    }

    override fun close() {
        // При выходе незавершённая запись остаётся журналом на диске для восстановления.
        recorder.close(); audio.stop(); scope.cancel()
        runBlocking { withTimeoutOrNull(5000) { scope.coroutineContext[Job]?.join() } }
        lock.release(); lockChannel.close()
    }
}

fun bundledEnvironment(resources: Path): Map<String, String> {
    fun executable(name: String): String = resources.resolve("bin/$name").toAbsolutePath().toString().also {
        require(Files.isRegularFile(Path.of(it)) && Files.isExecutable(Path.of(it))) { "В приложении отсутствует $name. Переустановите 3rdBrain целиком." }
    }
    fun model(name: String): String = resources.resolve("models/$name").toAbsolutePath().toString().also {
        require(Files.isRegularFile(Path.of(it)) && Files.size(Path.of(it)) > 1_000_000) { "В приложении отсутствует модель $name. Переустановите 3rdBrain целиком." }
    }
    return mapOf(
        "THIRDBRAIN_WHISPER_CLI" to executable("whisper-cli"),
        "THIRDBRAIN_WHISPER_MODEL" to model("ggml-small.bin"),
        "THIRDBRAIN_LLAMA_CLI" to executable("llama-completion"),
        "THIRDBRAIN_LLAMA_MODEL" to model("Qwen3-4B-Q4_K_M.gguf"),
        "THIRDBRAIN_FFMPEG" to executable("ffmpeg"),
    )
}

class DesktopRepository(private val store: FileBrainStore, private val processing: LocalProcessing, private val scope: CoroutineScope) : BrainRepository {
    override suspend fun snapshot(): AppSnapshot = withContext(Dispatchers.IO) { store.snapshot() }
    override suspend fun createProject(draft: ProjectDraft): Project = withContext(Dispatchers.IO) { store.createProject(draft) }
    override suspend fun updateProject(id: String, update: ProjectUpdate): Project = withContext(Dispatchers.IO) { store.updateProject(id, update) }
    override suspend fun pinProject(id: String, pinned: Boolean): Project = withContext(Dispatchers.IO) { store.pinProject(id, pinned) }
    override suspend fun orderPins(ids: List<String>) { withContext(Dispatchers.IO) { store.orderPins(ids) } }
    override suspend fun updateCaptureDraft(id: String, update: CaptureDraftUpdate): Capture = withContext(Dispatchers.IO) { store.updateDraft(id, update) }
    override suspend fun distribute(id: String, request: DistributionRequest): Note = withContext(Dispatchers.IO) { store.distribute(id, request) }
    override suspend fun updateNote(id: String, update: NoteUpdate): Note = withContext(Dispatchers.IO) { store.updateNote(id, update) }
    override suspend fun reprocess(id: String): Capture = withContext(Dispatchers.IO) { processing.enqueue(id, scope) }
}
