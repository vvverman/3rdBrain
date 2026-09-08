@file:OptIn(ExperimentalWasmJsInterop::class)

package brain.web

import brain.model.Capture
import brain.ui.RecorderGateway
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

class BrowserRecorder(private val baseUrl: String) : RecorderGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun hasConsent(): Boolean = jsHasConsent().toString() == "yes"

    override suspend fun start() {
        val result = jsStartRecorder().await().toString()
        if (result.startsWith("ERROR:")) error(result.removePrefix("ERROR:"))
        jsRememberConsent()
    }

    override suspend fun pause() {
        val result = jsPauseRecorder().toString()
        if (result.startsWith("ERROR:")) error(result.removePrefix("ERROR:"))
    }

    override suspend fun resume() {
        val result = jsResumeRecorder().toString()
        if (result.startsWith("ERROR:")) error(result.removePrefix("ERROR:"))
    }

    override suspend fun stopAndUpload(): Capture {
        val response = jsStopAndUpload(baseUrl.toJsString()).await().toString()
        if (response.startsWith("ERROR:")) error(response.removePrefix("ERROR:"))
        return json.decodeFromString(response)
    }
}

private fun jsHasConsent(): JsString = js("localStorage.getItem('thirdbrain.mic-consent') === 'yes' ? 'yes' : 'no'")
private fun jsRememberConsent(): Unit = js("localStorage.setItem('thirdbrain.mic-consent', 'yes')")
private fun jsStartRecorder(): Promise<JsString> = js("""
(async () => {
    try {
        if (globalThis.__thirdBrainRecorder && globalThis.__thirdBrainRecorder.state !== 'inactive') return 'already';
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const chunks = [];
        const options = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? { mimeType: 'audio/webm;codecs=opus' } : undefined;
        const recorder = new MediaRecorder(stream, options);
        recorder.ondataavailable = event => { if (event.data && event.data.size > 0) chunks.push(event.data); };
        globalThis.__thirdBrainRecorder = recorder;
        globalThis.__thirdBrainStream = stream;
        globalThis.__thirdBrainChunks = chunks;
        recorder.start(1000);
        return recorder.mimeType || 'audio/webm';
    } catch (e) { return 'ERROR:' + (e && e.message ? e.message : String(e)); }
})()
""")
private fun jsPauseRecorder(): JsString = js("""
(() => {
    try {
        const r = globalThis.__thirdBrainRecorder;
        if (!r || r.state !== 'recording') return 'ERROR:Запись не активна';
        r.pause(); return 'ok';
    } catch (e) { return 'ERROR:' + (e && e.message ? e.message : String(e)); }
})()
""")
private fun jsResumeRecorder(): JsString = js("""
(() => {
    try {
        const r = globalThis.__thirdBrainRecorder;
        if (!r || r.state !== 'paused') return 'ERROR:Запись не на паузе';
        r.resume(); return 'ok';
    } catch (e) { return 'ERROR:' + (e && e.message ? e.message : String(e)); }
})()
""")
private fun jsStopAndUpload(baseUrl: JsString): Promise<JsString> = js("""
new Promise(resolve => {
    try {
        const recorder = globalThis.__thirdBrainRecorder;
        const stream = globalThis.__thirdBrainStream;
        const chunks = globalThis.__thirdBrainChunks || [];
        if (!recorder || recorder.state === 'inactive') { resolve('ERROR:Нет активной записи'); return; }
        recorder.onstop = async () => {
            try {
                const blob = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' });
                if (stream) stream.getTracks().forEach(t => t.stop());
                globalThis.__thirdBrainRecorder = null;
                globalThis.__thirdBrainStream = null;
                globalThis.__thirdBrainChunks = [];
                const form = new FormData();
                const ext = (recorder.mimeType || '').includes('mp4') ? 'm4a' : 'webm';
                form.append('audio', blob, 'capture.' + ext);
                const response = await fetch(baseUrl + '/api/captures/audio', { method: 'POST', body: form });
                const text = await response.text();
                resolve(response.ok ? text : 'ERROR:' + text);
            } catch (e) { resolve('ERROR:' + (e && e.message ? e.message : String(e))); }
        };
        recorder.stop();
    } catch (e) { resolve('ERROR:' + (e && e.message ? e.message : String(e))); }
})
""")
