package brain.desktop

import brain.domain.*
import brain.model.*
import brain.runtime.*
import brain.studio.*
import kotlinx.coroutines.*
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.concurrent.atomic.AtomicBoolean

class DesktopServices(val root: Path, val resources: Path, cpuOnly: Boolean = false) : AutoCloseable {
    private val lockChannel: FileChannel
    private val lock: java.nio.channels.FileLock
    private val closed = AtomicBoolean(false)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store: FileBrainStore
    val processing: LocalProcessing
    val studioProcessor: StudioProcessor
    val recorder: DesktopRecorder
    val audio: DesktopAudio
    val repository: StudioRepository
    val simulated = Files.exists(resources.resolve("demo-mode.txt"))
    init {
        Files.createDirectories(root)
        lockChannel = FileChannel.open(root.resolve(".desktop.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        lock = try { lockChannel.tryLock() ?: error("3rdBrain уже запущен") } catch(e: Exception) { lockChannel.close(); throw e }
        try {
            val env = if (simulated) mapOf("THIRDBRAIN_FFMPEG" to bundledExecutable(resources,"ffmpeg")) else bundledEnvironment(resources)
            store = FileBrainStore(root, runtimeStatus = { RuntimeStatus(localOnly=true, simulated=simulated) }, singleCurrent=true)
            val runner = DesktopInferenceRunner(cpuOnly)
            processing = LocalProcessing(store, env, runner)
            val prefs = PreferenceStore(root)
            val intelligence: Intelligence = if(simulated) DemoIntelligence() else LocalStudioIntelligence(env,root,runner)
            studioProcessor = StudioProcessor(store,prefs,intelligence,env.getValue("THIRDBRAIN_FFMPEG"),runner)
            repository = StudioDiskRepository(store,studioProcessor,prefs,scope)
            recorder = DesktopRecorder(root,store){studioProcessor.enqueue(it,scope)}
            audio = DesktopAudio(store,env.getValue("THIRDBRAIN_FFMPEG"),root,scope)
        } catch(e: Exception) { lock.release();lockChannel.close();scope.cancel();throw e }
    }
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        recorder.close();audio.stop();scope.cancel()
        runBlocking { withTimeoutOrNull(5000){scope.coroutineContext[Job]?.join()} }
        lock.release();lockChannel.close()
    }
}
private fun bundledExecutable(resources:Path,name:String):String = resources.resolve("bin/$name").toAbsolutePath().toString().also{
    require(Files.isRegularFile(Path.of(it))&&Files.isExecutable(Path.of(it))){"В пакете отсутствует $name. Переустановите приложение целиком."}
}
fun bundledEnvironment(resources:Path):Map<String,String> {
    fun model(name:String)=resources.resolve("models/$name").toAbsolutePath().toString().also{require(Files.isRegularFile(Path.of(it))&&Files.size(Path.of(it))>1_000_000){"В пакете отсутствует модель $name"}}
    return mapOf("THIRDBRAIN_WHISPER_CLI" to bundledExecutable(resources,"whisper-cli"),"THIRDBRAIN_WHISPER_MODEL" to model("ggml-small.bin"),
        "THIRDBRAIN_LLAMA_CLI" to bundledExecutable(resources,"llama-completion"),"THIRDBRAIN_LLAMA_MODEL" to model("Qwen3-4B-Q4_K_M.gguf"),
        "THIRDBRAIN_FFMPEG" to bundledExecutable(resources,"ffmpeg"))
}
