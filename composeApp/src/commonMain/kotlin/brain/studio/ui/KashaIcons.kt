package brain.studio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Единственный набор продуктовых иконок Kasha.
 *
 * Rest state: точная геометрия Phosphor Fill из утверждённого набора.
 * Motion включается только там, где текущий Compose-порт воспроизводит конкретный
 * паттерн из закреплённого animated-Phosphor source. Непортированные glyphs
 * намеренно остаются статичными: придуманная «похожая» анимация запрещена.
 */
enum class Glyph {
    RECORD, PLAY, PAUSE, STOP, SEND, HOME, FOLDER, TASKS, SETTINGS,
    BACK, NEXT, PLUS, DELETE, MAGIC, MORE, PIN, EDIT, UP, DOWN, CHECK,
}

private data class Motion(
    val rotate: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val duration: Int = 400,
    val returns: Boolean = true,
    val wiggle: Boolean = false,
)

/**
 * Только подтверждённые прямые порты.
 *
 * BACK/NEXT/UP/DOWN — ln-dev7/icons-animated: 40 units в viewport 256 за 0.4 s.
 * SETTINGS — ln-dev7 gear: 180° за 0.5 s, затем возврат при окончании interaction.
 * EDIT — ln-dev7 pencil: 0 → -3° → +3° → 0 за 0.4 s.
 *
 * HOME/LIST/TRASH/PLUS/CHECK в upstream анимируют отдельные части/path morph.
 * Пока эти части не перенесены в Compose буквально, они остаются статичными.
 */
private fun motion(glyph: Glyph): Motion? = when (glyph) {
    Glyph.BACK -> Motion(x = -3.75f)
    Glyph.NEXT -> Motion(x = 3.75f)
    Glyph.UP -> Motion(y = -3.75f)
    Glyph.DOWN -> Motion(y = 3.75f)
    Glyph.SETTINGS -> Motion(rotate = 180f, duration = 500, returns = false)
    Glyph.EDIT -> Motion(rotate = 3f, duration = 400, wiggle = true)
    else -> null
}

private fun vector(glyph: Glyph): ImageVector = ImageVector.Builder(
    name = "KashaPhosphor${glyph.name}",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 256f,
    viewportHeight = 256f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(PhosphorFillPaths.getValue(glyph)).toNodes(),
        fill = SolidColor(Color.Black),
    )
}.build()

@Composable
fun KashaIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    animated: Boolean = false,
) {
    val image = remember(glyph) { vector(glyph) }
    val spec = remember(glyph) { motion(glyph) }
    val phase = remember(glyph) { Animatable(0f) }

    LaunchedEffect(animated, glyph, spec) {
        if (spec == null) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        when {
            !animated -> phase.animateTo(
                0f,
                tween((spec.duration / 2).coerceAtLeast(100), easing = FastOutSlowInEasing),
            )
            spec.wiggle -> {
                phase.snapTo(0f)
                phase.animateTo(-1f, tween(spec.duration / 3, easing = FastOutSlowInEasing))
                phase.animateTo(1f, tween(spec.duration / 3, easing = FastOutSlowInEasing))
                phase.animateTo(0f, tween(spec.duration / 3, easing = FastOutSlowInEasing))
            }
            spec.returns -> {
                phase.snapTo(0f)
                phase.animateTo(1f, tween(spec.duration / 2, easing = FastOutSlowInEasing))
                phase.animateTo(0f, tween(spec.duration / 2, easing = FastOutSlowInEasing))
            }
            else -> phase.animateTo(1f, tween(spec.duration, easing = FastOutSlowInEasing))
        }
    }

    val density = LocalDensity.current
    val x = with(density) { (spec?.x ?: 0f).dp.toPx() }
    val y = with(density) { (spec?.y ?: 0f).dp.toPx() }

    Icon(
        imageVector = image,
        contentDescription = null,
        tint = color,
        modifier = if (spec == null) modifier else modifier.graphicsLayer {
            rotationZ = spec.rotate * phase.value
            translationX = x * phase.value
            translationY = y * phase.value
        },
    )
}

@Composable
fun Symbol(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    animated: Boolean = false,
) = KashaIcon(glyph, modifier, color, animated)
