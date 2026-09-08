package brain.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brain.model.Note
import brain.model.Project
import kotlinx.coroutines.launch

@Composable
fun ProjectsScreen(state: BrainAppState, modifier: Modifier) {
    val note = state.selectedNoteId?.let { id -> state.snapshot.notes.firstOrNull { it.id == id } }
    val project = state.selectedProjectId?.let(state::project)
    when {
        note != null -> NoteScreen(state, note, modifier)
        project != null -> ProjectDetail(state, project, modifier)
        else -> ProjectList(state, modifier)
    }
}

@Composable
private fun ProjectList(state: BrainAppState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        Header("Проекты") { TextButton(onClick = { state.creatingProject = true }) { Text("Создать") } }
        val projects = state.orderedProjects(null)
        if (projects.isEmpty()) EmptyState("Пока нет проектов", "Создайте проект и опишите, что в него складывать.")
        else LazyColumn(Modifier.fillMaxSize()) {
            items(projects, key = { it.id }) { project ->
                Column(Modifier.fillMaxWidth().clickable { state.selectedProjectId = project.id }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            if (project.description.isNotBlank()) Text(project.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${state.notes(project).size}", color = MaterialTheme.colorScheme.outline)
                    }
                    Row {
                        TextButton(onClick = { scope.launch { state.togglePin(project) } }) { Text(if (project.pinned) "Открепить" else "Закрепить") }
                        TextButton(onClick = { state.editingProject = project }) { Text("Править") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProjectDetail(state: BrainAppState, project: Project, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { state.selectedProjectId = null }) { Text("Назад") }
            Text(project.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { state.editingProject = project }) { Text("Править") }
        }
        if (project.instruction.isNotBlank()) Text(project.instruction, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val notes = state.notes(project)
        if (notes.isEmpty()) EmptyState("Пока нет заметок", "После записи сохраните новую мысль в этот проект.")
        else LazyColumn(Modifier.fillMaxSize()) {
            items(notes, key = { it.id }) { note ->
                Column(Modifier.fillMaxWidth().clickable { state.selectedNoteId = note.id }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(note.body, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NoteScreen(state: BrainAppState, note: Note, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var title by remember(note.id, note.title) { mutableStateOf(note.title) }
    var body by remember(note.id, note.body) { mutableStateOf(note.body) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { state.selectedNoteId = null }) { Text("Назад") }
            Text("Заметка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { state.updateNote(note, title, body) } }) { Text("Сохранить") }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Заголовок") })
            TextField(body, { body = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Текст") })
        }
    }
}

@Composable
fun ProjectEditor(state: BrainAppState, project: Project?) {
    val scope = rememberCoroutineScope()
    var title by remember(project?.id) { mutableStateOf(project?.title ?: "") }
    var description by remember(project?.id) { mutableStateOf(project?.description ?: "") }
    var instruction by remember(project?.id) { mutableStateOf(project?.instruction ?: "") }
    AlertDialog(
        onDismissRequest = { state.creatingProject = false; state.editingProject = null },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = {
                scope.launch {
                    if (project == null) state.createProject(title, description, instruction)
                    else state.updateProject(project, title, description, instruction)
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = { state.creatingProject = false; state.editingProject = null }) { Text("Отмена") } },
        title = { Text(if (project == null) "Новый проект" else "Настройки проекта") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название") })
                TextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Описание") }, minLines = 2)
                TextField(instruction, { instruction = it }, Modifier.fillMaxWidth(), label = { Text("Что сюда складывать") }, minLines = 4)
            }
        },
    )
}
