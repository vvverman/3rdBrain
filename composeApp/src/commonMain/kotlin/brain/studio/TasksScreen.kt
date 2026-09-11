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
    fun t(key: String) = KashaCopy.text(s.language, key) ?: s.tr(key)
    val labels = mapOf(
        SortMode.ALPHABETICAL to t("sortAlphabetical"),
        SortMode.CREATED to t("sortCreated"),
        SortMode.UPDATED to t("sortUpdated"),
        SortMode.MANUAL to t("sortManual"),
    )

    Column(Modifier.fillMaxSize()) {
        Heading(t("tasks"))
        KashaSortBar(s.preferences.taskSort, labels, { scope.launch { s.setTaskSort(it) } })
        Spacer(Modifier.height(14.dp))

        val tasks = s.tasks()
        if (tasks.isEmpty()) {
            Text(
                t("noTasks"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            KashaReorderableList(
                items = tasks,
                key = { it.id },
                manual = s.preferences.taskSort == SortMode.MANUAL,
                onManualOrder = { ids -> scope.launch { s.reorderTasks(ids) } },
                modifier = Modifier.fillMaxSize(),
            ) { task, dragging ->
                KashaListCard(
                    onClick = {},
                    modifier = Modifier.graphicsLayer { alpha = if (dragging) .72f else 1f },
                ) {
                    Text(
                        task.text,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
