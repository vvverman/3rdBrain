package brain.desktop

import brain.domain.AudioGateway
import brain.runtime.*
import kotlinx.coroutines.*
import java.nio.file.*
import javax.sound.sampled.*

class DesktopAudio(private val store: FileBrainStore, private val ffmpeg: String, private val root: Path, private val scope: CoroutineScope) : AudioGateway {
    private var job: Job? = null
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var generation = 0L
    override suspend fun playCapture(captureId: String, compact: Boolean, fromSeconds: Double, rate: Double) {
        require(rate in 0.5..2.0 && fromSeconds.isFinite() && fromSeconds >= 0)
        stop(); val ownGeneration = generation
        val path = withContext(Dispatchers.IO) {
            val capture = store.capture(captureId) ?: error("Источник не найден")
            val target = Files.createTempFile(root, ".playback-", ".wav")
            try {
                JvmCommandRunner().run(listOf(ffmpeg, "-nostdin", "-v", "error", "-y", "-ss", fromSeconds.toString(),
                    "-i", store.resolveAudio(capture, compact).toString(), "-af", "atempo=$rate", "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", target.toString()), 300)
                target
            } catch (e: Exception) { Files.deleteIfExists(target); throw e }
        }
        if (generation != ownGeneration) { withContext(Dispatchers.IO) { Files.deleteIfExists(path) }; return }
        val stream = withContext(Dispatchers.IO) { AudioSystem.getAudioInputStream(path.toFile()) }
        val output = try { withContext(Dispatchers.IO) { AudioSystem.getSourceDataLine(stream.format).also { it.open(stream.format); it.start() } } }
        catch (e: Exception) { stream.close(); Files.deleteIfExists(path); throw e }
        line = output
        job = scope.launch {
            try {
                stream.use { audio ->
                    val buffer = ByteArray(8192)
                    while (isActive && generation == ownGeneration) {
                        val count = audio.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    if (isActive && generation == ownGeneration) output.drain()
                }
            } finally {
                output.close(); if (line === output) line = null
                Files.deleteIfExists(path)
            }
        }
    }
    override fun stop() { generation++; job?.cancel(); job = null; line?.stop(); line?.close(); line = null }
}
