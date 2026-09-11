package brain.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import brain.model.SortMode

@Composable
fun KashaSortBar(mode: SortMode, labels: Map<SortMode, String>, onSelect: (SortMode) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SortMode.entries.forEach { item ->
            val selected = item == mode
            Text(
                labels.getValue(item),
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (selected) c.primary else c.surfaceVariant.copy(alpha = .52f))
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(item) },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) c.onPrimary else c.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun KashaDestinationSwitch(value: DestinationKind, noteLabel: String, taskLabel: String, onChange: (DestinationKind) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceVariant.copy(alpha = .52f))
            .padding(3.dp)
            .selectableGroup(),
    ) {
        listOf(DestinationKind.NOTE to noteLabel, DestinationKind.TASK to taskLabel).forEach { (kind, label) ->
            val selected = value == kind
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) c.surface else Color.Transparent)
                    .selectable(
                        selected = selected,
                        interactionSource = source,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onChange(kind) },
                    )
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onSurface else c.onSurfaceVariant)
            }
        }
    }
}
