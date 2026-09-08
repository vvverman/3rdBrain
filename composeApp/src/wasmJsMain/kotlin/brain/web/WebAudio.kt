@file:OptIn(ExperimentalWasmJsInterop::class)
package brain.web

import brain.domain.AudioGateway
import kotlinx.coroutines.await
import kotlin.js.*

class WebAudioGateway(private val baseUrl: String) : AudioGateway {
    override suspend fun playCapture(captureId: String, compact: Boolean, fromSeconds: Double, rate: Double) {
        val url = "$baseUrl/api/captures/$captureId/audio?compact=$compact"
        val result = play(url.toJsString(), fromSeconds, rate).await().toString()
        if (result.startsWith("ERROR:")) error(result.removePrefix("ERROR:"))
    }
    override fun stop() { stopAudio() }
}
private fun play(url: JsString, from: Double, rate: Double): Promise<JsString> = js("globalThis.thirdBrainPlatform.play(url, from, rate)")
private fun stopAudio(): Unit = js("globalThis.thirdBrainPlatform.stopAudio()")
