package brain.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brain.model.Capture
import kotlinx.coroutines.launch

@Composable
fun RoutingScreen(state: BrainAppState, route: RouteStep, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val capture = state.capture(route.captureId)
    if (capture == null) {
        Column(modifier.padding(20.dp)) { Text("Запись пока не загружена"); TextButton(onClick = { state.route = null }) { Text("Назад") } }
        return
    }
    val project = route.projectId?.let(state::project)
    var title by remember(capture.id, capture.title) { mutableStateOf(capture.title) }
    var text by remember(capture.id, capture.textToSave) { mutableStateOf(capture.textToSave) }
    val editable = !capture.status.isWorking && capture.noteId == null
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { state.route = null }) { Text(if (capture.noteId == null) "Отложить" else "Назад к заметке") }
        Text(if (capture.noteId != null) "Источник заметки" else project?.title ?: "Куда сохранить?", style = MaterialTheme.typography.headlineSmall)
        if (capture.status.isWorking) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(capture.status.labelRu()) }
        if (capture.message.isNotBlank()) Text(capture.message, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), readOnly = !editable, label = { Text("Заголовок") })
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), readOnly = !editable,
            label = { Text(if (capture.llmApplied) "Оформленная заметка" else "Текст без ИИ-оформления") }, minLines = 4, maxLines = 8)
        if (editable) {
            Row {
                TextButton(enabled = !state.busy, onClick = { scope.launch { state.saveCaptureDraft(capture, title, text) } }) { Text("Сохранить черновик") }
                TextButton(enabled = !state.busy, onClick = { scope.launch { state.reprocess(capture) } }) { Text("Обработать") }
            }
        }
        SourceControls(state, capture)
        if (editable && project == null) {
            HorizontalDivider()
            Text(if (capture.rankingApplied) "Выше — подходящие. Закреплённые всегда первые." else "Закреплённые первые, остальные по названию. ИИ-сортировка не выполнена.", style = MaterialTheme.typography.bodySmall)
            state.orderedProjects(capture).forEach { candidate ->
                Column(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { state.route = RouteStep(capture.id, candidate.id) }.padding(vertical = 10.dp)) {
                    Text(candidate.title, fontWeight = FontWeight.SemiBold)
                    if (candidate.pinned) Text("Закреплён", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (candidate.instruction.isNotBlank()) Text(candidate.instruction, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.snapshot.projects.isEmpty()) Text("Создайте проект — запись останется во входящих.")
            TextButton(onClick = { state.creatingProject = true }) { Text("Создать проект") }
        } else if (editable && project != null) {
            Button(enabled = text.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch { state.saveAndDistribute(capture, project, null, title, text) }
            }) { Text("Новая заметка") }
            Text("Или добавить в конец существующей", style = MaterialTheme.typography.titleSmall)
            state.notes(project).forEach { note ->
                Column(Modifier.fillMaxWidth().clickable(enabled = text.isNotBlank() && !state.busy) {
                    scope.launch { state.saveAndDistribute(capture, project, note, title, text) }
                }.padding(vertical = 10.dp)) {
                    Text(note.title, fontWeight = FontWeight.Medium)
                    Text(note.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onClick = { state.route = RouteStep(capture.id) }) { Text("Назад к проектам") }
        }
    }
}

@Composable
fun SourceControls(state: BrainAppState, capture: Capture) {
    val scope = rememberCoroutineScope()
    var compact by remember(capture.id) { mutableStateOf(capture.compactAudioFileName != null) }
    var rate by remember { mutableStateOf(1.0) }
    var expanded by remember(capture.id) { mutableStateOf(false) }
    HorizontalDivider()
    Text("Аудиоисточник", style = MaterialTheme.typography.titleSmall)
    if (capture.compactAudioFileName != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !compact, onClick = { state.audio.stop(); compact = false }, label = { Text("Оригинал") })
            FilterChip(selected = compact, onClick = { state.audio.stop(); compact = true }, label = { Text("Без длинных пауз") })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { scope.launch { state.playSource(capture, compact, rate = rate) } }) { Text("Слушать") }
        OutlinedButton(onClick = { state.audio.stop() }) { Text("Стоп") }
        TextButton(onClick = { rate = when (rate) { 1.0 -> 1.25; 1.25 -> 1.5; 1.5 -> 2.0; else -> 1.0 } }) { Text("${rate}×") }
    }
    Text("Прослушивание ставит микрофон на паузу. Скорость применяется при следующем запуске.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть транскрипт" else "Полный транскрипт и таймкоды") }
    if (expanded) {
        Text(capture.transcript.ifBlank { "Транскрипция ещё не получена" })
        capture.pieces.forEach { piece ->
            TextButton(onClick = { scope.launch { state.playSource(capture, compact, piece.start, rate) } }) {
                Text("${piece.start.toInt() / 60}:${(piece.start.toInt() % 60).toString().padStart(2, '0')} · ${piece.text}")
            }
        }
    }
}
