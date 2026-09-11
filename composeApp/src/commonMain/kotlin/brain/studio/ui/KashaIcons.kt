package brain.studio

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Единственный набор продуктовых иконок Kasha.
 * Геометрия: Phosphor Icons Fill (MIT). Motion-язык: адаптация Iconimate (MIT) для Compose Multiplatform.
 * Никакие Material Icons / SF Symbols / Lucide в продуктовом UI не используются.
 */
enum class Glyph {
    RECORD, PLAY, PAUSE, STOP, SEND, HOME, FOLDER, TASKS, SETTINGS,
    BACK, NEXT, PLUS, DELETE, MAGIC, MORE, PIN, EDIT, UP, DOWN, CHECK,
}

private data class Motion(val rotate: Float = 0f, val x: Float = 0f, val y: Float = 0f, val scale: Float = 0f)

private fun motion(glyph: Glyph): Motion = when (glyph) {
    Glyph.RECORD -> Motion(scale = .08f)
    Glyph.PLAY -> Motion(x = 2.4f, scale = .035f)
    Glyph.PAUSE -> Motion(scale = -.045f)
    Glyph.STOP -> Motion(scale = -.06f)
    Glyph.SEND -> Motion(rotate = -7f, x = 3.5f, y = -2.5f)
    Glyph.HOME -> Motion(y = -1.6f, scale = .035f)
    Glyph.FOLDER -> Motion(rotate = -3.5f, y = -1.4f)
    Glyph.TASKS -> Motion(x = 1.8f, scale = .03f)
    Glyph.SETTINGS -> Motion(rotate = 16f)
    Glyph.BACK -> Motion(x = -2.6f)
    Glyph.NEXT -> Motion(x = 2.6f)
    Glyph.PLUS -> Motion(rotate = 12f, scale = .045f)
    Glyph.DELETE -> Motion(rotate = -4f, y = 1.8f)
    Glyph.MAGIC -> Motion(rotate = 7f, y = -1.8f, scale = .04f)
    Glyph.MORE -> Motion(scale = .07f)
    Glyph.PIN -> Motion(rotate = 7f, y = -1.2f)
    Glyph.EDIT -> Motion(x = 1.8f, y = -1.8f)
    Glyph.UP -> Motion(y = -2.6f)
    Glyph.DOWN -> Motion(y = 2.6f)
    Glyph.CHECK -> Motion(scale = .08f)
}

private val paths = mapOf(
    Glyph.RECORD to "M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm72-88a72,72,0,1,1-72-72A72.08,72.08,0,0,1,200,128Z",
    Glyph.PLAY to "M240,128a15.74,15.74,0,0,1-7.6,13.51L88.32,229.65a16,16,0,0,1-16.2.3A15.86,15.86,0,0,1,64,216.13V39.87a15.86,15.86,0,0,1,8.12-13.82,16,16,0,0,1,16.2.3L232.4,114.49A15.74,15.74,0,0,1,240,128Z",
    Glyph.PAUSE to "M216,48V208a16,16,0,0,1-16,16H160a16,16,0,0,1-16-16V48a16,16,0,0,1,16-16h40A16,16,0,0,1,216,48ZM96,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16H96a16,16,0,0,0,16-16V48A16,16,0,0,0,96,32Z",
    Glyph.STOP to "M216,56V200a16,16,0,0,1-16,16H56a16,16,0,0,1-16-16V56A16,16,0,0,1,56,40H200A16,16,0,0,1,216,56Z",
    Glyph.SEND to "M240,127.89a16,16,0,0,1-8.18,14L63.9,237.9A16.15,16.15,0,0,1,56,240a16,16,0,0,1-15-21.33l27-79.95A4,4,0,0,1,71.72,136H144a8,8,0,0,0,8-8.53,8.19,8.19,0,0,0-8.26-7.47h-72a4,4,0,0,1-3.79-2.72l-27-79.94A16,16,0,0,1,63.84,18.07l168,95.89A16,16,0,0,1,240,127.89Z",
    Glyph.HOME to "M224,120v96a8,8,0,0,1-8,8H160a8,8,0,0,1-8-8V164a4,4,0,0,0-4-4H108a4,4,0,0,0-4,4v52a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V120a16,16,0,0,1,4.69-11.31l80-80a16,16,0,0,1,22.62,0l80,80A16,16,0,0,1,224,120Z",
    Glyph.FOLDER to "M216,72H131.31L104,44.69A15.88,15.88,0,0,0,92.69,40H40A16,16,0,0,0,24,56V200.62A15.41,15.41,0,0,0,39.39,216h177.5A15.13,15.13,0,0,0,232,200.89V88A16,16,0,0,0,216,72ZM40,56H92.69l16,16H40Z",
    Glyph.TASKS to "M208,32H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V48A16,16,0,0,0,208,32ZM117.66,149.66l-32,32a8,8,0,0,1-11.32,0l-16-16a8,8,0,0,1,11.32-11.32L80,164.69l26.34-26.35a8,8,0,0,1,11.32,11.32Zm0-64-32,32a8,8,0,0,1-11.32,0l-16-16A8,8,0,0,1,69.66,90.34L80,100.69l26.34-26.35a8,8,0,0,1,11.32,11.32ZM192,168H144a8,8,0,0,1,0-16h48a8,8,0,0,1,0,16Zm0-64H144a8,8,0,0,1,0-16h48a8,8,0,0,1,0,16Z",
    Glyph.SETTINGS to "M32,80a8,8,0,0,1,8-8H77.17a28,28,0,0,1,53.66,0H216a8,8,0,0,1,0,16H130.83a28,28,0,0,1-53.66,0H40A8,8,0,0,1,32,80Zm184,88H194.83a28,28,0,0,0-53.66,0H40a8,8,0,0,0,0,16H141.17a28,28,0,0,0,53.66,0H216a8,8,0,0,0,0-16Z",
    Glyph.BACK to "M168,48V208a8,8,0,0,1-13.66,5.66l-80-80a8,8,0,0,1,0-11.32l80-80A8,8,0,0,1,168,48Z",
    Glyph.NEXT to "M181.66,133.66l-80,80A8,8,0,0,1,88,208V48a8,8,0,0,1,13.66-5.66l80,80A8,8,0,0,1,181.66,133.66Z",
    Glyph.PLUS to "M208,32H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V48A16,16,0,0,0,208,32ZM184,136H136v48a8,8,0,0,1-16,0V136H72a8,8,0,0,1,0-16h48V72a8,8,0,0,1,16,0v48h48a8,8,0,0,1,0,16Z",
    Glyph.DELETE to "M216,48H176V40a24,24,0,0,0-24-24H104A24,24,0,0,0,80,40v8H40a8,8,0,0,0,0,16h8V208a16,16,0,0,0,16,16H192a16,16,0,0,0,16-16V64h8a8,8,0,0,0,0-16ZM112,168a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Zm48,0a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Zm0-120H96V40a8,8,0,0,1,8-8h48a8,8,0,0,1,8,8Z",
    Glyph.MAGIC to "M248,152a8,8,0,0,1-8,8H224v16a8,8,0,0,1-16,0V160H192a8,8,0,0,1,0-16h16V128a8,8,0,0,1,16,0v16h16A8,8,0,0,1,248,152ZM56,72H72V88a8,8,0,0,0,16,0V72h16a8,8,0,0,0,0-16H88V40a8,8,0,0,0-16,0V56H56a8,8,0,0,0,0,16ZM184,192h-8v-8a8,8,0,0,0-16,0v8h-8a8,8,0,0,0,0,16h8v8a8,8,0,0,0,16,0v-8h8a8,8,0,0,0,0-16ZM219.31,80,80,219.31a16,16,0,0,1-22.62,0L36.68,198.63a16,16,0,0,1,0-22.63L176,36.69a16,16,0,0,1,22.63,0l20.68,20.68A16,16,0,0,1,219.31,80ZM208,68.69,187.31,48l-32,32L176,100.69Z",
    Glyph.MORE to "M224,80H32A16,16,0,0,0,16,96v64a16,16,0,0,0,16,16H224a16,16,0,0,0,16-16V96A16,16,0,0,0,224,80ZM60,140a12,12,0,1,1,12-12A12,12,0,0,1,60,140Zm68,0a12,12,0,1,1,12-12A12,12,0,0,1,128,140Zm68,0a12,12,0,1,1,12-12A12,12,0,0,1,196,140Z",
    Glyph.PIN to "M235.33,104l-53.47,53.65c4.56,12.67,6.45,33.89-13.19,60A15.93,15.93,0,0,1,157,224c-.38,0-.75,0-1.13,0a16,16,0,0,1-11.32-4.69L96.29,171,53.66,213.66a8,8,0,0,1-11.32-11.32L85,159.71l-48.3-48.3A16,16,0,0,1,38,87.63c25.42-20.51,49.75-16.48,60.4-13.14L152,20.7a16,16,0,0,1,22.63,0l60.69,60.68A16,16,0,0,1,235.33,104Z",
    Glyph.EDIT to "M227.31,73.37,182.63,28.68a16,16,0,0,0-22.63,0L36.69,152A15.86,15.86,0,0,0,32,163.31V208a16,16,0,0,0,16,16H92.69A15.86,15.86,0,0,0,104,219.31L227.31,96a16,16,0,0,0,0-22.63ZM192,108.68,147.31,64l24-24L216,84.68Z",
    Glyph.UP to "M215.39,163.06A8,8,0,0,1,208,168H48a8,8,0,0,1-5.66-13.66l80-80a8,8,0,0,1,11.32,0l80,80A8,8,0,0,1,215.39,163.06Z",
    Glyph.DOWN to "M213.66,101.66l-80,80a8,8,0,0,1-11.32,0l-80-80A8,8,0,0,1,48,88H208a8,8,0,0,1,5.66,13.66Z",
    Glyph.CHECK to "M216,40H40A16,16,0,0,0,24,56V200a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V56A16,16,0,0,0,216,40ZM205.66,85.66l-96,96a8,8,0,0,1-11.32,0l-40-40a8,8,0,0,1,11.32-11.32L104,164.69l90.34-90.35a8,8,0,0,1,11.32,11.32Z",
)

private fun vector(glyph: Glyph): ImageVector = ImageVector.Builder(
    name = "KashaPhosphor${glyph.name}",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 256f,
    viewportHeight = 256f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(paths.getValue(glyph)).toNodes(),
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
    val transition = rememberInfiniteTransition(label = "KashaIcon-${glyph.name}")
    val phase = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(440, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "KashaIconMotion-${glyph.name}",
    ).value
    val m = motion(glyph)
    val density = LocalDensity.current
    val x = with(density) { m.x.dp.toPx() }
    val y = with(density) { m.y.dp.toPx() }
    val amount = if (animated) phase else 0f
    Icon(
        imageVector = image,
        contentDescription = null,
        tint = color,
        modifier = modifier.graphicsLayer {
            rotationZ = m.rotate * amount
            translationX = x * amount
            translationY = y * amount
            val s = 1f + m.scale * kotlin.math.abs(amount)
            scaleX = s
            scaleY = s
        },
    )
}

/** Временное совместимое имя, чтобы продуктовые экраны переходили на Kasha UI без второго набора иконок. */
@Composable
fun Symbol(glyph: Glyph, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, animated: Boolean = false) =
    KashaIcon(glyph, modifier, color, animated)
