package brain.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import brain.ui.*
import kotlinx.coroutines.*
import java.awt.Dimension
import java.nio.file.*
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", "3rdBrain")
    val resources = Path.of(System.getProperty("compose.application.resources.dir")
        ?: System.getenv("THIRDBRAIN_BUNDLE_RESOURCES") ?: "desktopApp/bundle/common").toAbsolutePath()
    val selfTest = args.indexOf("--self-test")
    if (selfTest >= 0) {
        try {
            require(args.size > selfTest + 2) { "Нужны каталог отчёта и тестовый WAV" }
            runBlocking { SelfTest.run(resources, Path.of(args[selfTest + 1]), Path.of(args[selfTest + 2])) }
            exitProcess(0)
        } catch (e: Throwable) { e.printStackTrace(); exitProcess(1) }
    }
    val root = System.getenv("THIRDBRAIN_HOME")?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), "Library", "Application Support", "3rdBrain")
    val services = try { DesktopServices(root.toAbsolutePath(), resources) }
    catch (e: Exception) { JOptionPane.showMessageDialog(null, e.message, "3rdBrain", JOptionPane.ERROR_MESSAGE); return }
    val state = BrainAppState(services.repository, services.recorder, services.audio)
    val smokeAt = args.indexOf("--ui-smoke")
    val smokeOutput = if (smokeAt >= 0 && args.size > smokeAt + 1) Path.of(args[smokeAt + 1]) else null
    Thread.setDefaultUncaughtExceptionHandler { _, error ->
        runCatching { Files.writeString(root.resolve("last-error.log"), error.stackTraceToString()) }
        error.printStackTrace()
    }
    try {
        application {
            val scope = rememberCoroutineScope()
            var closing by remember { mutableStateOf(false) }
            Window(
                onCloseRequest = {
                    if (!closing) {
                        closing = true
                        scope.launch { withContext(Dispatchers.IO) { services.close() }; exitApplication() }
                    }
                },
                title = "3rdBrain",
                state = rememberWindowState(width = 430.dp, height = 820.dp, position = WindowPosition(Alignment.Center)),
                resizable = true,
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(390, 620)
                    if (smokeOutput != null) {
                        delay(5000)
                        Files.createDirectories(smokeOutput)
                        Files.writeString(smokeOutput.resolve("ui-ready.txt"), "3rdBrain window visible=${window.isShowing}; ${window.width}x${window.height}; java=${System.getProperty("java.home")}")
                        delay(8000)
                        services.close(); exitApplication()
                    }
                }
                MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                    BrainApp(state)
                }
            }
        }
    } finally { runCatching { services.close() } }
}
