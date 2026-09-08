package brain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brain.model.Capture
import brain.model.CaptureStatus
import kotlinx.coroutines.launch

@Composable
fun BrainApp(state: BrainAppState) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { state.launch() }
    LaunchedEffect(Unit) { state.pollProcessing() }
    LaunchedEffect(state.isRecording) { if (state.isRecording) state.tickRecordingClock() }

    Box(Modifier.fillMaxSize().background(Color(0xFF101310)), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxHeight().fillMaxWidth().widthIn(max = 430.dp)) {
            Scaffold(
                bottomBar = {
                    Column {
                        RecorderBar(state) { task -> scope.launch { task() } }
                        NavigationBar {
                            NavItem("Входящие", state.tab == MainTab.INBOX) { state.tab = MainTab.INBOX }
                            NavItem("Проекты", state.tab == MainTab.PROJECTS) { state.tab = MainTab.PROJECTS }
                            NavItem("Настройки", state.tab == MainTab.SETTINGS) { state.tab = MainTab.SETTINGS }
                        }
                    }
                },
            ) { padding ->
                when (state.tab) {
                    MainTab.INBOX -> InboxScreen(state, Modifier.padding(padding))
                    MainTab.PROJECTS -> ProjectsScreen(state, Modifier.padding(padding))
                    MainTab.SETTINGS -> SettingsScreen(state, Modifier.padding(padding))
                }
            }
        }
    }

    if (state.onboarding) Onboarding(state)
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = state::clearError,
            confirmButton = { TextButton(onClick = state::clearError) { Text("Понятно") } },
            title = { Text("Не удалось выполнить действие") },
            text = { Text(message) },
        )
    }
    state.route?.let { RoutingDialog(state, it) }
    if (state.creatingProject) ProjectEditor(state, null)
    state.editingProject?.let { ProjectEditor(state, it) }
}

@Composable
private fun RowScope.NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Box(Modifier.size(11.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) },
        label = { Text(label) },
    )
}

@Composable
fun Header(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun InboxScreen(state: BrainAppState, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Header("Входящие")
        val inbox = state.inbox()
        if (inbox.isEmpty()) EmptyState("Здесь появятся ваши мысли", "Запись всегда под рукой внизу экрана.")
        else LazyColumn(Modifier.fillMaxSize()) {
            items(inbox, key = { it.id }) { capture ->
                CaptureRow(capture) { state.route = RouteStep(capture.id) }
            }
        }
    }
}

@Composable
private fun CaptureRow(capture: Capture, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(capture.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (capture.status in setOf(CaptureStatus.QUEUED, CaptureStatus.TRANSCRIBING, CaptureStatus.POLISHING)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Text(capture.textToSave.ifBlank { capture.message.ifBlank { "Аудио сохранено" } }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(capture.status.labelRu(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
    HorizontalDivider()
}

@Composable
private fun SettingsScreen(state: BrainAppState, modifier: Modifier) {
    val status = state.snapshot.runtime
    Column(modifier.fillMaxSize()) {
        Header("Настройки")
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusCard("Локальный runtime", if (status.localOnly) "Только этот компьютер" else "Проверьте настройки")
            StatusCard("Whisper", if (status.whisperConfigured) "Готов" else "Не настроен")
            StatusCard("Локальная LLM", if (status.llmConfigured) "Готова" else "Не настроена")
            if (status.message.isNotBlank()) Text(status.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Web и desktop используют тот же мобильный интерфейс. Широкая desktop-компоновка намеренно отсутствует.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(title: String, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecorderBar(state: BrainAppState, onAction: (suspend () -> Unit) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f), tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.isRecording) Color(0xFFD14A3A) else MaterialTheme.colorScheme.outline))
            Text(if (state.isRecording || state.isPaused) formatTime(state.elapsedSeconds) else "Готово к записи", modifier = Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.Medium)
            when {
                state.busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                state.isRecording -> {
                    OutlinedButton(onClick = { onAction { state.pauseRecording() } }) { Text("Пауза") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onAction { state.stopRecording() } }) { Text("Готово") }
                }
                state.isPaused -> {
                    OutlinedButton(onClick = { onAction { state.resumeRecording() } }) { Text("Продолжить") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onAction { state.stopRecording() } }) { Text("Готово") }
                }
                else -> Button(onClick = { onAction { state.startRecording() } }) { Text("Записать") }
            }
        }
    }
}

@Composable
private fun Onboarding(state: BrainAppState) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = {},
        confirmButton = { Button(onClick = { scope.launch { state.consentAndStart() } }) { Text("Разрешить микрофон и начать") } },
        title = { Text("Откройте. Скажите. Сохраните.") },
        text = { Text("После разрешения микрофона 3rdBrain начинает запись при открытии. Запись всегда видна на нижней панели.") },
    )
}

private fun formatTime(seconds: Long): String = "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

fun CaptureStatus.labelRu(): String = when (this) {
    CaptureStatus.RECORDING -> "Запись"
    CaptureStatus.QUEUED -> "Ожидает обработки"
    CaptureStatus.TRANSCRIBING -> "Распознавание"
    CaptureStatus.POLISHING -> "Оформление"
    CaptureStatus.READY -> "Готово"
    CaptureStatus.NEEDS_MODEL -> "Нужна локальная модель"
    CaptureStatus.FAILED -> "Ошибка обработки"
}
