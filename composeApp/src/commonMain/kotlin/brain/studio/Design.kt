package brain.studio

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
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

enum class Glyph { RECORD, PLAY, PAUSE, STOP, SEND, HOME, FOLDER, SETTINGS, BACK, NEXT, PLUS, DELETE, MAGIC, MORE, PIN, UP, DOWN, CHECK }

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
                Glyph.PAUSE -> { drawRoundRect(color,Offset(7f,5f),Size(3f,14f),CornerRadius(1.2f));drawRoundRect(color,Offset(14f,5f),Size(3f,14f),CornerRadius(1.2f)) }
                Glyph.SEND -> { path(5f,11f,12f,4f,19f,11f); line(12f,4f,12f,21f) }
                Glyph.STOP -> drawRoundRect(color,Offset(6f,6f),Size(12f,12f),CornerRadius(2f))
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
                Glyph.UP -> path(5f,15f,12f,8f,19f,15f)
                Glyph.DOWN -> path(5f,9f,12f,16f,19f,9f)
                Glyph.CHECK -> path(4f,12f,10f,18f,20f,6f)
            }
        }
    }
}

@Composable fun IconAction(label: String, glyph: Glyph, onClick: () -> Unit, filled: Boolean = false, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.size(46.dp).clip(CircleShape).background(if (filled) c.primary else c.surfaceVariant.copy(alpha=.65f))
        .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Symbol(glyph, color = if (filled) c.onPrimary else c.onSurface)
    }
}
@Composable fun Action(label: String, onClick: () -> Unit, primary: Boolean = false, glyph: Glyph? = null, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Row(modifier.heightIn(min=50.dp).clip(RoundedCornerShape(18.dp)).background(if (primary) c.primary else c.surface)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}
        .padding(horizontal=18.dp, vertical=14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val fg = (if (primary) c.onPrimary else c.onSurface).copy(alpha=if(enabled) 1f else .45f)
        if (glyph != null) { Symbol(glyph, Modifier.size(18.dp), fg); Spacer(Modifier.width(10.dp)) }
        Text(label, style=MaterialTheme.typography.labelLarge, color=fg)
    }
}
@Composable fun QuietAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(label, modifier.clip(RoundedCornerShape(12.dp)).clickable(role=Role.Button,onClick=onClick).padding(vertical=12.dp,horizontal=4.dp), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun Editor(value: String, onValue: (String)->Unit, hint: String, modifier: Modifier=Modifier, title: Boolean=false, readOnly: Boolean=false) {
    val c=MaterialTheme.colorScheme
    BasicTextField(value,onValue,modifier.semantics { contentDescription=hint }, readOnly=readOnly,
        textStyle=(if(title) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge).copy(color=c.onSurface),
        cursorBrush=SolidColor(c.onSurface), decorationBox={ inner ->
            Box { if(value.isEmpty()) Text(hint, style=if(title) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,color=c.onSurfaceVariant.copy(alpha=.6f)); inner() }
        })
}
@Composable fun Wave(peaks: List<Float>, modifier: Modifier=Modifier, progress: Float?=null) {
    val c=MaterialTheme.colorScheme
    Canvas(modifier) {
        val data=if(peaks.isEmpty()) List(80){0f} else peaks
        val step=size.width/data.size
        data.forEachIndexed { i, p ->
            val height=(p.coerceIn(0f,1f)*size.height*.88f).coerceAtLeast(1.2.dp.toPx())
            val color=if(progress != null && i.toFloat()/data.size > progress) c.onSurface.copy(alpha=.22f) else c.onSurface.copy(alpha=.83f)
            drawLine(color,Offset((i+.5f)*step,(size.height-height)/2),Offset((i+.5f)*step,(size.height+height)/2),
                (step*.34f).coerceIn(.8.dp.toPx(),2.2.dp.toPx()),StrokeCap.Round)
        }
    }
}
@Composable fun BotanicalMark(modifier: Modifier) {
    val c=MaterialTheme.colorScheme
    Canvas(modifier) {
        val w=size.width; val h=size.height
        val stem=Path().apply { moveTo(w*.84f,h*1.1f);cubicTo(w*.7f,h*.65f,w*.4f,h*.3f,w*.2f,-h*.1f) }
        drawPath(stem,c.primary,style=Stroke(2.dp.toPx()))
        repeat(7) { i ->
            val y=h*(.05f+i*.145f);val x=w*(.25f+i*.085f)
            val leaf=Path().apply { moveTo(x,y);cubicTo(x-w*.5f,y-h*.14f,x-w*.46f,y+h*.13f,x+w*.025f,y+h*.12f);cubicTo(x-w*.1f,y+h*.07f,x-w*.08f,y+h*.015f,x,y);close() }
            drawPath(leaf,c.primary.copy(alpha=1f-i*.04f))
            val right=Path().apply { moveTo(x,y+h*.03f);cubicTo(x+w*.1f,y-h*.2f,x+w*.55f,y-h*.19f,x+w*.37f,y-h*.025f);cubicTo(x+w*.22f,y-h*.06f,x+w*.05f,y+h*.015f,x,y+h*.03f);close() }
            drawPath(right,c.primary.copy(alpha=.9f-i*.03f))
        }
    }
}
@Composable fun ToggleRow(label: String, value: Boolean, onChange: (Boolean)->Unit) {
    val c=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(role=Role.Switch) { onChange(!value) }
        .semantics { contentDescription=label; toggleableState=if(value) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }, verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f).padding(end=20.dp),style=MaterialTheme.typography.bodyMedium)
        Box(Modifier.size(40.dp,24.dp).clip(CircleShape).background(if(value)c.primary else c.outline.copy(alpha=.55f)).padding(3.dp),contentAlignment=if(value) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(if(value)c.onPrimary else c.surface))
        }
    }
}
@Composable fun ProcessingRing(modifier: Modifier=Modifier) {
    val rotation by rememberInfiniteTransition().animateFloat(0f,360f,infiniteRepeatable(tween(1800,easing=LinearEasing)))
    val c=MaterialTheme.colorScheme
    Canvas(modifier) {
        drawCircle(c.outline.copy(alpha=.2f),size.minDimension*.4f,style=Stroke(2.dp.toPx()))
        drawArc(c.primary,rotation,80f,false,Offset(size.width*.1f,size.height*.1f),Size(size.width*.8f,size.height*.8f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
    }
}
fun clock(seconds: Long): String = "${seconds.coerceAtLeast(0)/60}:${(seconds.coerceAtLeast(0)%60).toString().padStart(2,'0')}"
