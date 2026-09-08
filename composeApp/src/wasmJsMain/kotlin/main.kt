import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import brain.ui.BrainApp
import brain.ui.BrainAppState
import brain.web.BrowserRecorder
import brain.web.WebAudioGateway
import brain.web.WebBrainRepository

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val baseUrl = brain.web.runtimeBaseUrl()
    val state = BrainAppState(
        repository = WebBrainRepository(baseUrl),
        recorder = BrowserRecorder(baseUrl),
        audio = WebAudioGateway(baseUrl),
    )
    ComposeViewport(viewportContainerId = "webApp") {
        MaterialTheme { BrainApp(state) }
    }
}
