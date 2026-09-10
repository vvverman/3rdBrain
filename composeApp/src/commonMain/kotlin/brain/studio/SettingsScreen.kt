package brain.studio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CalmSlider(value:Float,onChange:(Float)->Unit,range:ClosedFloatingPointRange<Float>,steps:Int,label:String,onFinished:()->Unit){
    val c=MaterialTheme.colorScheme
    Slider(value,onChange,valueRange=range,steps=steps,onValueChangeFinished=onFinished,
        modifier=Modifier.fillMaxWidth().height(40.dp).semantics{contentDescription=label},
        thumb={Box(Modifier.size(14.dp).clip(CircleShape).background(c.primary))},
        track={Canvas(Modifier.fillMaxWidth().height(3.dp)){
            val y=size.height/2;val fraction=((value-range.start)/(range.endInclusive-range.start)).coerceIn(0f,1f)
            drawLine(c.outline.copy(alpha=.32f),Offset(0f,y),Offset(size.width,y),3.dp.toPx(),StrokeCap.Round)
            drawLine(c.primary,Offset(0f,y),Offset(size.width*fraction,y),3.dp.toPx(),StrokeCap.Round)
        }})
}
@Composable internal fun SettingsScreen(s:StudioState){
    val scope=rememberCoroutineScope();val p=s.preferences;val c=MaterialTheme.colorScheme
    fun save(value:Preferences){scope.launch{s.savePreferences(value)}}
    if(s.languagePage){
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=20.dp)){
            Heading(s.tr("appLanguage"),{s.languagePage=false},s.tr("back"))
            (listOf("system" to s.tr("systemLanguage"))+Languages.codes.zip(Languages.names)).forEach{(code,name)->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role=Role.Button){save(p.copy(language=code))}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(name,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium,color=if(p.language==code)c.onSurface else c.onSurfaceVariant)
                    if(p.language==code)Symbol(Glyph.CHECK,Modifier.size(19.dp))
                }
            }
        };return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=20.dp)){
        Heading(s.tr("settings"))
        Text(s.tr("recordSettings"),style=MaterialTheme.typography.titleSmall);Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surface).padding(18.dp)){
            Text(s.tr("quality"),style=MaterialTheme.typography.bodyMedium)
            var quality by remember(p.quality){mutableStateOf(p.quality.toFloat())}
            CalmSlider(quality,{quality=it},0f..3f,2,s.tr("quality")){save(p.copy(quality=quality.toInt()))}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(s.tr("economy"),style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant);Text(s.tr("high"),style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
            Spacer(Modifier.height(24.dp))
            Row{Text(s.tr("savedSpeed"),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text("${p.savedSpeed}×",style=MaterialTheme.typography.bodyMedium)}
            var speed by remember(p.savedSpeed){mutableStateOf(p.savedSpeed.toFloat())}
            CalmSlider(speed,{speed=it},1f..2f,3,s.tr("savedSpeed")){save(p.copy(savedSpeed=(kotlin.math.round(speed*4)/4).toDouble()))}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf("1×","1,5×","2×").forEach{Text(it,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}}
        }
        Spacer(Modifier.height(10.dp));ToggleRow(s.tr("autoRecord"),p.autoRecord){save(p.copy(autoRecord=it))}
        ToggleRow(s.tr("autoRoute"),p.autoRoute){save(p.copy(autoRoute=it))}
        Spacer(Modifier.height(24.dp));Text(s.tr("appearance"),style=MaterialTheme.typography.titleSmall);Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("system","light","dark").forEach{theme->Action(s.tr(theme),{save(p.copy(theme=theme))},primary=p.theme==theme,modifier=Modifier.weight(1f))}
        }
        Row(Modifier.fillMaxWidth().padding(top=14.dp).clickable(role=Role.Button){s.languagePage=true}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(s.tr("appLanguage"),style=MaterialTheme.typography.bodyMedium);Text(if(p.language=="system")s.tr("systemLanguage")else Languages.names[Languages.codes.indexOf(p.language)],style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
            Symbol(Glyph.NEXT,Modifier.size(18.dp))
        }
        if(s.repository.simulated){
            Spacer(Modifier.height(26.dp));Text(s.tr("demoSettings"),style=MaterialTheme.typography.titleSmall);Spacer(Modifier.height(12.dp))
            Text(s.tr("demoNotice"),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            listOf("idea","password","cooking").forEach{example->
                Row(Modifier.fillMaxWidth().clickable(role=Role.RadioButton){save(p.copy(demoExample=example))}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(s.tr(example),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    if(p.demoExample==example)Symbol(Glyph.CHECK,Modifier.size(16.dp))
                }
            }
        }
    }
}
