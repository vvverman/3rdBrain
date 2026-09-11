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
 * Motion: перенос в Compose паттернов из закреплённых MIT animated-Phosphor
 * snapshot-ов в third_party. React/Motion не являются runtime-зависимостью.
 */
enum class Glyph {
    RECORD, PLAY, PAUSE, STOP, SEND, HOME, FOLDER, TASKS, SETTINGS,
    BACK, NEXT, PLUS, DELETE, MAGIC, MORE, PIN, EDIT, UP, DOWN, CHECK,
}

private data class Motion(
    val rotate: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 0f,
    val duration: Int = 400,
    val returns: Boolean = true,
    val wiggle: Boolean = false,
)

private fun motion(glyph: Glyph): Motion = when (glyph) {
    // ln-dev7 animated Phosphor arrows: движение на 40 единиц в viewport 256.
    Glyph.BACK -> Motion(x = -3.75f)
    Glyph.NEXT -> Motion(x = 3.75f)
    Glyph.UP -> Motion(y = -3.75f)
    Glyph.DOWN -> Motion(y = 3.75f)

    // animated Phosphor gear: 180° / 0.5 s, возврат при окончании interaction.
    Glyph.SETTINGS -> Motion(rotate = 180f, duration = 500, returns = false)

    // animated Phosphor pencil: [0, -3, +3, 0] / 0.4 s.
    Glyph.EDIT -> Motion(rotate = 3f, duration = 400, wiggle = true)

    // animated Phosphor trash: небольшой lift крышки + compression корпуса.
    // Fill-глиф является единым path, поэтому переносим движение на весь силуэт.
    Glyph.DELETE -> Motion(y = -.47f, scale = -.05f, duration = 400)

    // В оригинале house прорисовывает path, list перестраивает три строки,
    // plus/check reveal-ят части glyph. Для единого Phosphor Fill path сохраняем
    // тот же короткий interaction-язык без подмены геометрии.
    Glyph.HOME -> Motion(scale = .06f, duration = 500)
    Glyph.TASKS -> Motion(scale = -.06f, duration = 420)
    Glyph.PLUS -> Motion(scale = .10f, duration = 300)
    Glyph.CHECK -> Motion(scale = .10f, duration = 400)

    // Остальные product glyphs используют restrained motion в том же диапазоне 0.3–0.5 s.
    Glyph.RECORD -> Motion(scale = .08f)
    Glyph.PLAY -> Motion(x = 2.4f, scale = .035f)
    Glyph.PAUSE -> Motion(scale = -.045f)
    Glyph.STOP -> Motion(scale = -.06f)
    Glyph.SEND -> Motion(rotate = -7f, x = 3.5f, y = -2.5f)
    Glyph.FOLDER -> Motion(rotate = -3.5f, y = -1.4f)
    Glyph.MAGIC -> Motion(rotate = 7f, y = -1.8f, scale = .04f)
    Glyph.MORE -> Motion(scale = .07f)
    Glyph.PIN -> Motion(rotate = 7f, y = -1.2f)
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

    LaunchedEffect(animated, glyph) {
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
    val x = with(density) { spec.x.dp.toPx() }
    val y = with(density) { spec.y.dp.toPx() }

    Icon(
        imageVector = image,
        contentDescription = null,
        tint = color,
        modifier = modifier.graphicsLayer {
            rotationZ = spec.rotate * phase.value
            translationX = x * phase.value
            translationY = y * phase.value
            val s = 1f + spec.scale * phase.value
            scaleX = s
            scaleY = s
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
