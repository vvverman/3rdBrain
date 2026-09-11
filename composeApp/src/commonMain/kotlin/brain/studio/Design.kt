package brain.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import brain.studio.resources.*
import org.jetbrains.compose.resources.Font

private val Ink = Color(0xFF1C1B29)
private val Paper = Color(0xFFF2F1F5)

@Composable fun StudioTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val family = FontFamily(Font(Res.font.onest_regular, FontWeight.Normal), Font(Res.font.onest_medium, FontWeight.Medium), Font(Res.font.onest_semibold, FontWeight.SemiBold))
    val colors = if (!dark) lightColorScheme(primary = Ink, onPrimary = Color.White, background = Paper,
        surface = Color.White, onSurface = Ink, onBackground = Ink, surfaceVariant = Color(0xFFE9E8EF),
        onSurfaceVariant = Color(0xFF706F7B), outline = Color(0xFFB8B7C1), error = Color(0xFFA33838))
    else darkColorScheme(primary = Color(0xFFF4F3F6), onPrimary = Ink, background = Color(0xFF1A1924),
        surface = Color(0xFF24232F), onSurface = Color(0xFFF4F3F6), onBackground = Color(0xFFF4F3F6),
        surfaceVariant = Color(0xFF302F3C), onSurfaceVariant = Color(0xFFAAA9B5), outline = Color(0xFF5B5968), error = Color(0xFFEB9696))
    fun style(size: Int, line: Int = size + 6, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
    MaterialTheme(colorScheme = colors, typography = Typography(
        displayLarge = style(62, 68), displayMedium = style(50, 54), displaySmall = style(38, 44),
        headlineLarge = style(32, 38, FontWeight.Medium), headlineMedium = style(27, 33, FontWeight.Medium), headlineSmall = style(24, 30),
        titleLarge = style(21, 28, FontWeight.Medium), titleMedium = style(17, 24, FontWeight.Medium), titleSmall = style(14, 20, FontWeight.Medium),
        bodyLarge = style(17, 27), bodyMedium = style(14, 21), bodySmall = style(12, 18),
        labelLarge = style(14, 18, FontWeight.Medium), labelMedium = style(12, 16), labelSmall = style(10, 14)
    ), content = content)
}

enum class Glyph { RECORD, PLAY, PAUSE, STOP, SEND, HOME, FOLDER, SETTINGS, BACK, NEXT, PLUS, DELETE, MAGIC, MORE, PIN, EDIT, UP, DOWN, CHECK }

/** Авторская геометрия. Ни Material Icons, ни SF Symbols. */
@Composable fun Symbol(glyph: Glyph, modifier: Modifier = Modifier.size(22.dp), color: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(color, Offset(a,b), Offset(c,d), 1.5f, StrokeCap.Round)
            fun path(vararg pts: Float) { val p = Path(); p.moveTo(pts[0],pts[1]); for (i in 2 until pts.size step 2) p.lineTo(pts[i],pts[i+1]); drawPath(p,color,style=stroke) }
            when (glyph) {
                Glyph.RECORD -> { drawCircle(color, 7f, Offset(12f,12f)); drawCircle(color.copy(alpha=.3f),10f,Offset(12f,12f),style=stroke) }
                Glyph.PLAY -> { val p=Path(); p.moveTo(8f,5f);p.lineTo(19f,12f);p.lineTo(8f,19f);p.close();drawPath(p,color) }
                Glyph.PAUSE -> { drawRoundRect(color,Offset(7f,5f),Size(3f,14f),androidx.compose.ui.geometry.CornerRadius(1.2f));drawRoundRect(color,Offset(14f,5f),Size(3f,14f),androidx.compose.ui.geometry.CornerRadius(1.2f)) }
                Glyph.SEND -> { path(5f,11f,12f,4f,19f,11f); line(12f,4f,12f,21f) }
                Glyph.STOP -> drawRoundRect(color,Offset(6f,6f),Size(12f,12f),androidx.compose.ui.geometry.CornerRadius(2f))
                Glyph.HOME -> { path(3f,11f,12f,3f,21f,11f);path(6f,10f,6f,21f,10f,21f,10f,15f,14f,15f,14f,21f,18f,21f,18f,10f) }
                Glyph.FOLDER -> { val p=Path();p.moveTo(3f,7f);p.lineTo(3f,20f);p.lineTo(21f,20f);p.lineTo(21f,8f);p.lineTo(12f,8f);p.lineTo(9f,4f);p.lineTo(3f,4f);p.close();drawPath(p,color,style=stroke) }
                Glyph.SETTINGS -> { line(5f,3f,5f,21f);line(12f,3f,12f,21f);line(19f,3f,19f,21f);drawCircle(color,2.6f,Offset(5f,8f));drawCircle(color,2.6f,Offset(12f,16f));drawCircle(color,2.6f,Offset(19f,10f)) }
                Glyph.BACK -> { path(14f,5f,7f,12f,14f,19f);line(7f,12f,21f,12f) }
                Glyph.NEXT -> path(9f,5f,16f,12f,9f,19f)
                Glyph.PLUS -> {line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
                Glyph.DELETE -> {path(6f,7f,7f,21f,17f,21f,18f,7f);line(4f,7f,20f,7f);path(9f,7f,9f,3f,15f,3f,15f,7f);line(10f,11f,10f,17f);line(14f,11f,14f,17f)}
                Glyph.MAGIC -> {path(12f,3f,14f,9f,20f,11f,14f,13f,12f,19f,10f,13f,4f,11f,10f,9f,12f,3f);line(20f,16f,20f,22f);line(17f,19f,23f,19f)}
                Glyph.MORE -> {listOf(5f,12f,19f).forEach { drawCircle(color,1.5f,Offset(it,12f)) }}
                Glyph.PIN -> {path(9f,3f,15f,3f,15f,10f,18f,13f,6f,13f,9f,10f,9f,3f);line(12f,13f,12f,21f)}
                Glyph.EDIT -> { path(5f,19f,6f,14f,16f,4f,20f,8f,10f,18f,5f,19f); line(14f,6f,18f,10f); line(4f,21f,20f,21f) }
                Glyph.UP -> path(5f,15f,12f,8f,19f,15f)
                Glyph.DOWN -> path(5f,9f,12f,16f,19f,9f)
                Glyph.CHECK -> path(4f,12f,10f,18f,20f,6f)
            }
        }
    }
}

/** Совместимые имена старого UI теперь только проксируют в закрытую библиотеку Brain UI. */
@Composable fun IconAction(label: String, glyph: Glyph, onClick: () -> Unit, filled: Boolean = false, modifier: Modifier = Modifier) =
    BrainIconButton(label, glyph, onClick, modifier, filled)

@Composable fun Action(label: String, onClick: () -> Unit, primary: Boolean = false, glyph: Glyph? = null, modifier: Modifier = Modifier, enabled: Boolean = true) =
    BrainButton(label, onClick, modifier, primary, glyph, enabled)

@Composable fun QuietAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    BrainQuietButton(label, onClick, modifier)

@Composable fun Editor(value: String, onValue: (String)->Unit, hint: String, modifier: Modifier=Modifier, title: Boolean=false, readOnly: Boolean=false) =
    BrainField(value, onValue, hint, modifier, title = title, multiline = !title, readOnly = readOnly, minHeight = if (title) 58.dp else 100.dp)

@Composable fun Wave(peaks: List<Float>, modifier: Modifier=Modifier, progress: Float?=null) = BrainWaveform(peaks, modifier, progress)
@Composable fun ToggleRow(label: String, value: Boolean, onChange: (Boolean)->Unit) = BrainSwitchRow(label, value, onChange)
@Composable fun ProcessingRing(modifier: Modifier=Modifier) = BrainProcessingRing(modifier)

fun clock(seconds: Long): String = "${seconds.coerceAtLeast(0)/60}:${(seconds.coerceAtLeast(0)%60).toString().padStart(2,'0')}"
