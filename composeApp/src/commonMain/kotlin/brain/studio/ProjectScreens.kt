package brain.studio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brain.domain.ProjectOrder
import brain.model.*
import kotlinx.coroutines.launch

@Composable
private fun ProjectLine(p: Project, onClick: () -> Unit, onEdit: (() -> Unit)? = null, editLabel: String = "") {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).clickable(role = Role.Button, onClick = onClick).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Symbol(if (p.pinned) Glyph.PIN else Glyph.FOLDER, Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium)
            val detail = p.instruction.ifBlank { p.description }
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onEdit != null) IconAction(editLabel, Glyph.MORE, onEdit, modifier = Modifier.size(32.dp))
        else Symbol(Glyph.NEXT, Modifier.size(16.dp), colors.onSurfaceVariant)
    }
}

@Composable
internal fun ProjectsScreen(s: StudioState) {
    val project = s.snapshot.projects.firstOrNull { it.id == s.selectedProjectId }
    val note = s.snapshot.notes.firstOrNull { it.id == s.selectedNoteId }
    val scope = rememberCoroutineScope()
    when {
        note != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 22.dp)) {
            Heading(s.tr("notes"), { s.selectedNoteId = null }, s.tr("back"))
            Text(note.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(22.dp))
            androidx.compose.foundation.text.selection.SelectionContainer { Text(note.body, style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(28.dp))
            Text(s.tr("sources"), style = MaterialTheme.typography.titleSmall)
            Text(s.tr("sourceHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.noteSources(note.id).forEach { source ->
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable(role = Role.Button) { scope.launch { s.requestListen(source.id) } }.padding(14.dp)) {
                    Text(source.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Wave(source.waveform, Modifier.weight(1f).height(30.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(clock(source.durationSeconds.toLong()), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        project != null -> Column {
            Heading(project.title, { s.selectedProjectId = null }, s.tr("back")) { IconAction(s.tr("edit"), Glyph.MORE, { s.editingProjectId = project.id }) }
            val notes = s.projectNotes(project.id)
            if (notes.isEmpty()) Text(s.tr("noNotes"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(notes, key = { it.id }) { n -> NoteLine(n) { s.openNote(n.id) } }
            }
        }
        else -> Column {
            Heading(s.tr("projects")) { IconAction(s.tr("newProject"), Glyph.PLUS, { s.beginProjectCreation() }) }
            val projects = ProjectOrder.sorted(s.snapshot.projects)
            if (projects.isEmpty()) Text(s.tr("noProjects"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(projects, key = { it.id }) { p -> ProjectLine(p, { s.selectedProjectId = p.id; s.selectedNoteId = null }, { s.editingProjectId = p.id }, s.tr("edit")) }
            }
        }
    }
}

@Composable
private fun NoteLine(note: Note, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).clickable(role = Role.Button, onClick = onClick).padding(18.dp)) {
        Text(note.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(note.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun DestinationScreen(s: StudioState) {
    val scope = rememberCoroutineScope()
    val project = s.snapshot.projects.firstOrNull { it.id == s.targetProjectId }
    Column(Modifier.fillMaxSize()) {
        Heading(if (project == null) s.tr("chooseProject") else project.title, { if (project == null) s.choosingProject = false else s.targetProjectId = null }, s.tr("back"))
        if (project == null) {
            // Действие доступно сразу, даже при длинном списке. Нижний плеер остаётся видимым.
            Action(s.tr("createProject"), { s.beginProjectCreation(fromPicker = true) }, glyph = Glyph.PLUS,
                enabled = !s.busy, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(s.orderedProjects(), key = { it.id }) { p -> ProjectLine(p, { s.targetProjectId = p.id }) }
            }
        } else {
            Action(s.tr("newNote"), { scope.launch { s.distribute(project.id) } }, primary = true, glyph = Glyph.PLUS, enabled = !s.busy, modifier = Modifier.fillMaxWidth())
            Text(s.tr("appendHint"), Modifier.padding(vertical = 22.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(s.projectNotes(project.id), key = { it.id }) { n -> NoteLine(n) { if (!s.busy) scope.launch { s.distribute(project.id, n.id) } } }
            }
        }
    }
}

@Composable
internal fun ProjectEditor(s: StudioState) {
    val scope = rememberCoroutineScope()
    val project = s.snapshot.projects.firstOrNull { it.id == s.editingProjectId }
    var title by remember(s.editingProjectId) { mutableStateOf(project?.title.orEmpty()) }
    var instruction by remember(s.editingProjectId) { mutableStateOf(project?.instruction.orEmpty()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
        Heading(if (project == null) s.tr("newProject") else s.tr("edit"), s::cancelProjectEdit, s.tr("back"))
        Editor(title, { title = it }, s.tr("projectName"), Modifier.fillMaxWidth(), title = true)
        Spacer(Modifier.height(24.dp))
        Editor(instruction, { instruction = it }, s.tr("instruction"), Modifier.fillMaxWidth().heightIn(min = 130.dp))
        Text(s.tr("instructionHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        Action(s.tr("save"), { scope.launch { if (project == null) s.createProject(title, instruction) else s.updateProject(project.id, title, instruction) } }, primary = true, enabled = title.isNotBlank() && !s.busy, modifier = Modifier.fillMaxWidth())
        if (project != null) {
            ToggleRow(s.tr("pin"), project.pinned) { scope.launch { s.pin(project) } }
            if (project.pinned) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action(s.tr("up"), { scope.launch { s.movePin(project, -1) } }, glyph = Glyph.UP, modifier = Modifier.weight(1f))
                Action(s.tr("down"), { scope.launch { s.movePin(project, 1) } }, glyph = Glyph.DOWN, modifier = Modifier.weight(1f))
            }
        }
    }
}
