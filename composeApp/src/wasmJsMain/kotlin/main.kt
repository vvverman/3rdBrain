import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import brain.ui.BrainApp
import brain.ui.BrainAppState
import brain.web.BrowserRecorder
import brain.web.WebAudioGateway
import brain.web.WebBrainRepository

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val baseUrl = "http://127.0.0.1:8787"
    val state = BrainAppState(
        repository = WebBrainRepository(baseUrl),
        recorder = BrowserRecorder(baseUrl),
        audio = WebAudioGateway(baseUrl),
    )
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "3rdBrain") {
        MaterialTheme { BrainApp(state) }
    }
}
