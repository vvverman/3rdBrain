package brain.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brain.model.Capture
import brain.model.CaptureStatus
import brain.model.Note
import brain.model.Project
import kotlinx.coroutines.launch

@Composable
fun RoutingDialog(state: BrainAppState, route: RouteStep) {
    val scope = rememberCoroutineScope()
    val capture = state.capture(route.captureId) ?: return
    val project = route.projectId?.let(state::project)
    var title by remember(capture.id, capture.title) { mutableStateOf(capture.title) }
    var text by remember(capture.id, capture.textToSave) { mutableStateOf(capture.textToSave) }

    AlertDialog(
        onDismissRequest = { state.route = null },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { state.route = null }) { Text("Отложить") } },
        title = { Text(if (project == null) "Куда сохранить?" else project.title) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Заголовок") })
                TextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Текст") }, minLines = 4, maxLines = 8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { state.saveCaptureDraft(capture, title, text) } }) { Text("Черновик") }
                    OutlinedButton(onClick = { scope.launch { state.playSource(capture) } }) { Text("Источник") }
                }
                if (capture.status in setOf(CaptureStatus.NEEDS_MODEL, CaptureStatus.FAILED)) {
                    Text(capture.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { scope.launch { state.reprocess(capture) } }) { Text("Повторить обработку") }
                }
                HorizontalDivider()
                if (project == null) ProjectChoices(state, capture)
                else NoteChoices(state, capture, project, title, text)
            }
        },
    )
}

@Composable
private fun ProjectChoices(state: BrainAppState, capture: Capture) {
    Text("Проекты", style = MaterialTheme.typography.titleMedium)
    state.orderedProjects(capture).forEach { project ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { state.route = RouteStep(capture.id, project.id) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(project.title, fontWeight = FontWeight.Medium)
                if (project.instruction.isNotBlank()) Text(project.instruction, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (project.pinned) Text("Закреплён", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NoteChoices(state: BrainAppState, capture: Capture, project: Project, title: String, text: String) {
    val scope = rememberCoroutineScope()
    Button(
        onClick = { scope.launch { state.saveCaptureDraft(capture, title, text); state.distribute(state.capture(capture.id) ?: capture, project, null) } },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Новая заметка") }

    Text("Или добавить в конец существующей", style = MaterialTheme.typography.titleSmall)
    state.notes(project).forEach { note ->
        ExistingNote(note) {
            scope.launch {
                state.saveCaptureDraft(capture, title, text)
                state.distribute(state.capture(capture.id) ?: capture, project, note)
            }
        }
    }
    TextButton(onClick = { state.route = RouteStep(capture.id) }) { Text("Назад к проектам") }
}

@Composable
private fun ExistingNote(note: Note, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp)) {
        Text(note.title, fontWeight = FontWeight.Medium)
        Text(note.body, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
