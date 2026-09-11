package brain.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

const val KASHA_VERSION = "1.1.4"

/**
 * Единая точка бренда. Сюда подключается официальный logo asset (regular/solid),
 * чтобы ни одна платформа не хранила собственную версию знака.
 */
@Composable
fun KashaBrandSlot(modifier: Modifier = Modifier) {
    // Текст намеренно используется как безопасный fallback до помещения исходного binary asset в composeResources.
    // Не рисуем приблизительную копию официального знака.
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("Kasha", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun KashaSplash(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        KashaBrandSlot(Modifier.align(Alignment.Center).sizeIn(minWidth = 180.dp, minHeight = 180.dp))
        Text(
            "v$KASHA_VERSION",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
