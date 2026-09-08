@file:OptIn(ExperimentalWasmJsInterop::class)
package brain.web

import brain.domain.RecorderGateway
import brain.model.Capture
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlin.js.*

fun runtimeBaseUrl(): String = platformBase().toString()
private fun platformBase(): JsString = js("globalThis.thirdBrainPlatform.baseUrl()")
class BrowserRecorder(private val baseUrl: String) : RecorderGateway {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun hasConsent(): Boolean = consent()
    override suspend fun hasPending(): Boolean = pending().await().toString() == "true"
    override fun phase(): String = recorderPhase().toString()
    override suspend fun start() { checked(startRecorder().await()) }
    override suspend fun pause() { checked(pauseRecorder()) }
    override suspend fun resume() { checked(resumeRecorder()) }
    override suspend fun stopAndUpload(): Capture = json.decodeFromString(checked(stopRecorder(baseUrl.toJsString()).await()))
    override suspend fun recoverPending(): Capture = json.decodeFromString(checked(recoverRecorder(baseUrl.toJsString()).await()))
    private fun checked(result: JsString): String = result.toString().also { if (it.startsWith("ERROR:")) error(it.removePrefix("ERROR:")) }
}
private fun consent(): Boolean = js("globalThis.thirdBrainPlatform.consent()")
private fun pending(): Promise<JsString> = js("globalThis.thirdBrainPlatform.pending().then(v => String(v))")
private fun recorderPhase(): JsString = js("globalThis.thirdBrainPlatform.phase()")
private fun startRecorder(): Promise<JsString> = js("globalThis.thirdBrainPlatform.start()")
private fun pauseRecorder(): JsString = js("globalThis.thirdBrainPlatform.pause()")
private fun resumeRecorder(): JsString = js("globalThis.thirdBrainPlatform.resume()")
private fun stopRecorder(base: JsString): Promise<JsString> = js("globalThis.thirdBrainPlatform.stop(base)")
private fun recoverRecorder(base: JsString): Promise<JsString> = js("globalThis.thirdBrainPlatform.recover(base)")
