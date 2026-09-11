package brain.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brain.model.SortMode
import kotlinx.coroutines.launch

@Composable
internal fun TasksScreen(s: StudioState) {
    val scope = rememberCoroutineScope()
    val labels = mapOf(
        SortMode.ALPHABETICAL to s.tr("sortAlphabetical"),
        SortMode.CREATED to s.tr("sortCreated"),
        SortMode.UPDATED to s.tr("sortUpdated"),
        SortMode.MANUAL to s.tr("sortManual"),
    )
    Column(Modifier.fillMaxSize()) {
        Heading(s.tr("tasks"))
        KashaSortBar(s.preferences.taskSort, labels, { scope.launch { s.setTaskSort(it) } })
        Spacer(Modifier.height(14.dp))
        val tasks = s.tasks()
        if (tasks.isEmpty()) {
            Text(s.tr("noTasks"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            KashaReorderableList(
                items = tasks,
                key = { it.id },
                manual = s.preferences.taskSort == SortMode.MANUAL,
                onManualOrder = { ids -> scope.launch { s.reorderTasks(ids) } },
                modifier = Modifier.fillMaxSize(),
            ) { task, dragging ->
                KashaListCard(onClick = {}, modifier = Modifier.graphicsLayer { alpha = if (dragging) .72f else 1f }) {
                    KashaIcon(Glyph.TASKS, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant, animated = dragging)
                    Spacer(Modifier.width(13.dp))
                    Text(task.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
