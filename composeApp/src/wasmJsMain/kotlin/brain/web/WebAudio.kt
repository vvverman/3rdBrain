@file:OptIn(ExperimentalWasmJsInterop::class)

package brain.web

import brain.ui.AudioGateway
import kotlin.js.JsString
import kotlin.js.toJsString

class WebAudioGateway(private val baseUrl: String) : AudioGateway {
    override suspend fun playCapture(captureId: String) {
        val result = jsPlayAudio(("$baseUrl/api/captures/$captureId/audio").toJsString()).toString()
        if (result.startsWith("ERROR:")) error(result.removePrefix("ERROR:"))
    }
    override fun stop() { jsStopAudio() }
}

private fun jsPlayAudio(url: JsString): JsString = js("""
(() => {
    try {
        if (globalThis.__thirdBrainAudio) globalThis.__thirdBrainAudio.pause();
        const player = new Audio(url);
        globalThis.__thirdBrainAudio = player;
        player.play();
        return 'ok';
    } catch (e) { return 'ERROR:' + (e && e.message ? e.message : String(e)); }
})()
""")
private fun jsStopAudio(): Unit = js("""{
    if (globalThis.__thirdBrainAudio) {
        globalThis.__thirdBrainAudio.pause();
        globalThis.__thirdBrainAudio = null;
    }
}""")
