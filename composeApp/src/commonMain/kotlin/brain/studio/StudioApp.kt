package brain.studio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import brain.model.*
import brain.domain.ProjectOrder
import kotlinx.coroutines.*

@Composable fun StudioApp(state:StudioState){
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){state.launch()}
    LaunchedEffect(Unit){state.poll()}
    LaunchedEffect(state.editRevision){if(state.editRevision>0){delay(400);state.autosave()}}
    StudioTheme(state.preferences.theme){
        val c=MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(),color=c.background,contentColor=c.onSurface){
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                Column(Modifier.widthIn(max=430.dp).fillMaxWidth().fillMaxHeight().padding(horizontal=24.dp)){
                    Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){
                        Text("3rdBrain",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,letterSpacing=(-.4).sp)
                        if(state.repository.simulated)Text(state.tr("demoBadge"),style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant,maxLines=1)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()){
                        when{
                            state.error!=null->Notice(state.tr(state.error!!),state.tr("ok")){state.error=null}
                            state.confirmDelete->Confirmation(state.tr("deleteTitle"),state.tr("deleteBody"),state.tr("delete"),state.tr("cancel"),{scope.launch{state.discard()}},{state.confirmDelete=false})
                            state.confirmListenId!=null->Confirmation(state.tr("confirmListen"),state.tr("preserveRecording"),state.tr("stopAndPlay"),state.tr("cancel"),{scope.launch{state.confirmStopAndListen()}},{state.confirmListenId=null})
                            state.editingProjectId!=null->ProjectEditor(state)
                            state.choosingProject->DestinationScreen(state)
                            else->when(state.tab){Tab.HOME->HomeScreen(state);Tab.PROJECTS->ProjectsScreen(state);Tab.SETTINGS->SettingsScreen(state)}
                        }
                    }
                    GlobalPlayer(state)
                    Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=14.dp),horizontalArrangement=Arrangement.SpaceEvenly){
                        listOf(Triple(Tab.HOME,"home",Glyph.HOME),Triple(Tab.PROJECTS,"projects",Glyph.FOLDER),Triple(Tab.SETTINGS,"settings",Glyph.SETTINGS)).forEach{(tab,key,icon)->
                            val selected=state.tab==tab
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(role=Role.Tab){state.navigate(tab)}
                                .semantics(mergeDescendants=true){this.selected=selected}.padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                Symbol(icon,Modifier.size(20.dp),if(selected)c.onSurface else c.onSurfaceVariant)
                                Spacer(Modifier.height(5.dp));Text(state.tr(key),style=MaterialTheme.typography.labelSmall,color=if(selected)c.onSurface else c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable internal fun Heading(title:String,back:(()->Unit)?=null,backLabel:String="",action:(@Composable()->Unit)?=null){
    Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=24.dp),verticalAlignment=Alignment.CenterVertically){
        if(back!=null){IconAction(backLabel,Glyph.BACK,back);Spacer(Modifier.width(12.dp))}
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);action?.invoke()
    }
}
@Composable private fun ProjectLine(p:Project,onClick:()->Unit,onEdit:(()->Unit)?=null,editLabel:String=""){
    val c=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).clickable(role=Role.Button,onClick=onClick).padding(17.dp),verticalAlignment=Alignment.CenterVertically){
        Symbol(if(p.pinned)Glyph.PIN else Glyph.FOLDER,Modifier.size(21.dp));Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)){
            Text(p.title,style=MaterialTheme.typography.titleMedium)
            val detail=p.instruction.ifBlank{p.description}
            if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(onEdit!=null)IconAction(editLabel,Glyph.MORE,onEdit,modifier=Modifier.size(32.dp))else Symbol(Glyph.NEXT,Modifier.size(16.dp),c.onSurfaceVariant)
    }
}
@Composable private fun ProjectsScreen(s:StudioState){
    val project=s.snapshot.projects.firstOrNull{it.id==s.selectedProjectId};val note=s.snapshot.notes.firstOrNull{it.id==s.selectedNoteId};val scope=rememberCoroutineScope()
    when{
        note!=null->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=22.dp)){
            Heading(s.tr("notes"),{s.selectedNoteId=null},s.tr("back"))
            Text(note.title,style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(22.dp))
            androidx.compose.foundation.text.selection.SelectionContainer{Text(note.body,style=MaterialTheme.typography.bodyLarge)}
            Spacer(Modifier.height(28.dp));Text(s.tr("sources"),style=MaterialTheme.typography.titleSmall)
            Text(s.tr("sourceHint"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            s.noteSources(note.id).forEach{source->
                Column(Modifier.fillMaxWidth().padding(top=12.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable(role=Role.Button){scope.launch{s.requestListen(source.id)}}.padding(14.dp)){
                    Text(source.title,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Row(verticalAlignment=Alignment.CenterVertically){Wave(source.waveform,Modifier.weight(1f).height(30.dp));Spacer(Modifier.width(16.dp));Text(clock(source.durationSeconds.toLong()),style=MaterialTheme.typography.labelSmall)}
                }
            }
        }
        project!=null->Column{
            Heading(project.title,{s.selectedProjectId=null},s.tr("back")){IconAction(s.tr("edit"),Glyph.MORE,{s.editingProjectId=project.id})}
            val notes=s.projectNotes(project.id)
            if(notes.isEmpty())Text(s.tr("noNotes"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)){items(notes,key={it.id}){n->NoteLine(n){s.openNote(n.id)}}}
        }
        else->Column{
            Heading(s.tr("projects")){IconAction(s.tr("newProject"),Glyph.PLUS,{s.editingProjectId="new"})}
            val projects=ProjectOrder.sorted(s.snapshot.projects)
            if(projects.isEmpty())Text(s.tr("noProjects"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)){items(projects,key={it.id}){p->ProjectLine(p,{s.selectedProjectId=p.id;s.selectedNoteId=null},{s.editingProjectId=p.id},s.tr("edit"))}}
        }
    }
}
@Composable private fun NoteLine(n:Note,onClick:()->Unit){
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).clickable(role=Role.Button,onClick=onClick).padding(18.dp)){
        Text(n.title,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(6.dp))
        Text(n.body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun DestinationScreen(s:StudioState){
    val scope=rememberCoroutineScope();val project=s.snapshot.projects.firstOrNull{it.id==s.targetProjectId}
    Column(Modifier.fillMaxSize()){
        Heading(if(project==null)s.tr("chooseProject")else project.title,{if(project==null)s.choosingProject=false else s.targetProjectId=null},s.tr("back"))
        if(project==null){
            if(s.snapshot.projects.isEmpty())Text(s.tr("createInProjects"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)){items(s.orderedProjects(),key={it.id}){p->ProjectLine(p,{s.targetProjectId=p.id})}}
        }else{
            Action(s.tr("newNote"),{scope.launch{s.distribute(project.id)}},primary=true,glyph=Glyph.PLUS,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
            Text(s.tr("appendHint"),Modifier.padding(vertical=22.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)){items(s.projectNotes(project.id),key={it.id}){n->NoteLine(n){if(!s.busy)scope.launch{s.distribute(project.id,n.id)}}}}
        }
    }
}
@Composable private fun ProjectEditor(s:StudioState){
    val scope=rememberCoroutineScope();val p=s.snapshot.projects.firstOrNull{it.id==s.editingProjectId}
    var title by remember(s.editingProjectId){mutableStateOf(p?.title.orEmpty())};var instruction by remember(s.editingProjectId){mutableStateOf(p?.instruction.orEmpty())}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=20.dp)){
        Heading(if(p==null)s.tr("newProject")else s.tr("edit"),{s.editingProjectId=null},s.tr("back"))
        Editor(title,{title=it},s.tr("projectName"),Modifier.fillMaxWidth(),title=true)
        Spacer(Modifier.height(24.dp));Editor(instruction,{instruction=it},s.tr("instruction"),Modifier.fillMaxWidth().heightIn(min=130.dp))
        Text(s.tr("instructionHint"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp));Action(s.tr("save"),{scope.launch{if(p==null)s.createProject(title,instruction)else s.updateProject(p.id,title,instruction)}},primary=true,enabled=title.isNotBlank()&&!s.busy,modifier=Modifier.fillMaxWidth())
        if(p!=null){
            ToggleRow(s.tr("pin"),p.pinned){scope.launch{s.pin(p)}}
            if(p.pinned)Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Action(s.tr("up"),{scope.launch{s.movePin(p,-1)}},glyph=Glyph.UP,modifier=Modifier.weight(1f))
                Action(s.tr("down"),{scope.launch{s.movePin(p,1)}},glyph=Glyph.DOWN,modifier=Modifier.weight(1f))
            }
        }
    }
}
@Composable private fun Notice(text:String,label:String,action:()->Unit){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center){Text(text,style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(24.dp));Action(label,action,primary=true,modifier=Modifier.fillMaxWidth())}}
@Composable private fun Confirmation(title:String,body:String,confirm:String,cancel:String,onConfirm:()->Unit,onCancel:()->Unit){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center){Text(title,style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(18.dp));Text(body,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(28.dp));Action(confirm,onConfirm,primary=true,modifier=Modifier.fillMaxWidth());QuietAction(cancel,onCancel,Modifier.align(Alignment.CenterHorizontally))}}
