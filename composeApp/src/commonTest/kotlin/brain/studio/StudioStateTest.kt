package brain.studio

import brain.domain.*
import brain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StudioStateTest {
    private class Repo:StudioRepository {
        override val simulated=true
        var prefs=Preferences(autoRecord=false)
        var data=BrainData(projects=listOf(Project("p","Приложение")))
        var failSave=false;var failDiscard=false
        var snapshotGate: CompletableDeferred<Unit>? = null
        override suspend fun snapshot(): AppSnapshot {
            snapshotGate?.await()
            return AppSnapshot(data.projects,data.notes,data.captures,RuntimeStatus(simulated=true))
        }
        override suspend fun preferences()=prefs
        override suspend fun savePreferences(value:Preferences){prefs=value.validated()}
        override suspend fun createProject(draft:ProjectDraft):Project{data=data.addProject("p2",0,draft);return data.projects.last()}
        override suspend fun updateProject(id:String,update:ProjectUpdate):Project{data=data.updateProject(id,update);return data.projects.first{it.id==id}}
        override suspend fun pinProject(id:String,pinned:Boolean):Project{data=data.pinProject(id,pinned);return data.projects.first{it.id==id}}
        override suspend fun orderPins(ids:List<String>){data=data.orderPins(ids)}
        override suspend fun updateCaptureDraft(id:String,update:CaptureDraftUpdate):Capture{check(!failSave);data=data.updateDraft(id,update);return data.captures.first{it.id==id}}
        override suspend fun distribute(id:String,request:DistributionRequest):Note{val(next,note)=data.distribute(id,request,"n",0);data=next;return note}
        override suspend fun updateNote(id:String,update:NoteUpdate):Note{data=data.updateNote(id,update,0);return data.notes.first{it.id==id}}
        override suspend fun reprocess(id:String)=data.captures.first{it.id==id}
        override suspend fun tidy(id:String):Capture{val c=data.captures.first{it.id==id};return updateCaptureDraft(id,CaptureDraftUpdate(c.title,DemoIntelligence(0).tidy(c.textToSave,"ru")))}
        override suspend fun rank(id:String)=data.captures.first{it.id==id}
        override suspend fun discard(id:String){check(!failDiscard);data=data.copy(captures=data.captures.filterNot{it.id==id})}
        override suspend fun createDemo():Capture{val c=Capture("c",0,title="Тест",transcript="Старый текст",status=CaptureStatus.QUEUED,simulated=true);data=data.addCapture(c);return c}
        fun ready(){data=data.copy(captures=data.captures.map{it.copy(status=CaptureStatus.READY,audioFinalized=true,audioFileName="audio/c/saved.m4a")})}
    }
    private class Recorder(val repo:Repo):RecorderGateway {
        var status="idle";var starts=0;var recovered=false
        override suspend fun hasConsent()=true
        override suspend fun hasPending()=false
        override fun phase()=status
        override fun level()=if(status=="recording")0.5f else 0f
        override suspend fun start(){starts++;status="recording"}
        override suspend fun pause(){status="paused"}
        override suspend fun resume(){status="recording"}
        override suspend fun stopAndUpload():Capture{status="idle";return repo.createDemo()}
        override suspend fun recoverPending():Capture{recovered=true;return repo.createDemo()}
    }
    private class Audio:AudioGateway {
        var plays=0;var t=AudioTelemetry()
        override suspend fun playCapture(captureId:String,compact:Boolean,fromSeconds:Double,rate:Double){plays++;t=AudioTelemetry("playing",fromSeconds,10.0)}
        override suspend fun pause(){t=t.copy(phase="paused")}
        override suspend fun resume(){t=t.copy(phase="playing")}
        override fun telemetry()=t
        override fun stop(){t=AudioTelemetry()}
    }
    @Test fun disabledAutostartDoesNotRequestMic()=runTest{val r=Repo();val mic=Recorder(r);val s=StudioState(r,mic,Audio());s.launch();assertEquals(0,mic.starts)}
    @Test fun autostartRunsOnceAndOnlyWithoutCurrent()=runTest{val r=Repo().apply{prefs=Preferences(autoRecord=true)};val mic=Recorder(r);val s=StudioState(r,mic,Audio());s.launch();s.launch();assertEquals(1,mic.starts)}
    @Test fun unsentSessionSuppressesAutostart()=runTest{val r=Repo().apply{prefs=Preferences(autoRecord=true)};r.createDemo();r.ready();val mic=Recorder(r);val s=StudioState(r,mic,Audio());s.launch();assertEquals(0,mic.starts);assertNotNull(s.current)}
    @Test fun navigationDoesNotStopOrPauseRecording()=runTest{val r=Repo();val mic=Recorder(r);val s=StudioState(r,mic,Audio());s.launch();s.startRecording();s.navigate(Tab.PROJECTS);s.navigate(Tab.SETTINGS);assertEquals("recording",mic.status)}
    @Test fun otherAudioRequiresConfirmationWhileRecording()=runTest{val r=Repo();val mic=Recorder(r);val a=Audio();val s=StudioState(r,mic,a);s.launch();s.startRecording();s.requestListen("saved");assertEquals("saved",s.confirmListenId);assertEquals(0,a.plays);assertEquals("recording",mic.status);s.confirmListenId=null;assertEquals("recording",mic.status)}
    @Test fun confirmationSavesBeforePlaying()=runTest{val r=Repo();val mic=Recorder(r);val a=Audio();val s=StudioState(r,mic,a);s.launch();s.startRecording();s.requestListen("saved");s.confirmStopAndListen();assertEquals("idle",mic.status);assertNotNull(s.current);assertEquals(1,a.plays)}
    @Test fun playingBlocksRecording()=runTest{val r=Repo();val mic=Recorder(r);val s=StudioState(r,mic,Audio());s.launch();s.requestListen("saved");s.startRecording();assertEquals(0,mic.starts);assertEquals("stopPlayback",s.error)}
    @Test fun failedTextSaveCannotDistributeStaleText()=runTest{val r=Repo();r.createDemo();r.ready();val s=StudioState(r,Recorder(r),Audio());s.launch();s.editText("Изменено");r.failSave=true;s.send();assertFalse(s.choosingProject);assertEquals("Изменено",s.text);assertTrue(r.data.notes.isEmpty())}
    @Test fun textSurvivesNavigationAndFlush()=runTest{val r=Repo();r.createDemo();r.ready();val s=StudioState(r,Recorder(r),Audio());s.launch();s.editTitle("Мой заголовок");s.editText("Мои слова");s.navigate(Tab.PROJECTS);s.flush();s.refresh();assertEquals("Мои слова",s.text);assertEquals("Мои слова",r.data.captures.single().textToSave)}
    @Test fun autoRouteOnlyAfterResult()=runTest{val r=Repo().apply{prefs=Preferences(autoRecord=false,autoRoute=true)};val s=StudioState(r,Recorder(r),Audio());s.launch();s.demo();assertFalse(s.choosingProject);r.ready();s.refresh();assertTrue(s.choosingProject);assertTrue(r.data.notes.isEmpty())}
    @Test fun savedSessionClearsHome()=runTest{val r=Repo();r.createDemo();r.ready();val s=StudioState(r,Recorder(r),Audio());s.launch();s.distribute("p");assertNull(s.current);assertEquals("",s.text);assertEquals("p",r.data.notes.single().projectId)}
    @Test fun failedDiscardKeepsSession()=runTest{val r=Repo();r.createDemo();r.ready();r.failDiscard=true;val s=StudioState(r,Recorder(r),Audio());s.launch();s.discard();assertNotNull(s.current)}
    @Test fun localizationIsCompleteAndExplicit() {assertTrue(Copy.keys().size>60);for(key in Copy.keys())for(lang in Languages.codes)assertTrue(Copy.text(lang,key).isNotBlank())}

    @Test fun distributionPublishesSnapshotBeforeClosingPicker() = runTest {
        val repo = Repo(); repo.createDemo(); repo.ready()
        val state = StudioState(repo, Recorder(repo), Audio()); state.launch()
        state.choosingProject = true; state.targetProjectId = "p"
        val gate = CompletableDeferred<Unit>(); repo.snapshotGate = gate
        val save = launch { state.distribute("p") }; runCurrent()
        assertEquals(1, repo.data.notes.size)
        assertTrue(state.choosingProject)
        assertFalse(save.isCompleted)
        gate.complete(Unit); save.join()
        assertNull(state.current); assertFalse(state.choosingProject)
        assertEquals(1, state.projectNotes("p").size)
    }
    @Test fun projectEditorClosesOnlyAfterSnapshotRefresh() = runTest {
        val repo = Repo(); val state = StudioState(repo, Recorder(repo), Audio()); state.launch()
        state.editingProjectId = "new"
        val gate = CompletableDeferred<Unit>(); repo.snapshotGate = gate
        val save = launch { state.createProject("Новый проект", "") }; runCurrent()
        assertEquals("new", state.editingProjectId)
        gate.complete(Unit); save.join()
        assertNull(state.editingProjectId)
        assertTrue(state.snapshot.projects.any { it.title == "Новый проект" })
    }
    @Test fun leavingScreenDoesNotCancelCommittedSaveRefresh() = runTest {
        val repo = Repo(); repo.createDemo(); repo.ready()
        val state = StudioState(repo, Recorder(repo), Audio()); state.launch()
        state.attachActionScope(backgroundScope)
        val screen = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>(); repo.snapshotGate = gate
        val caller = screen.launch { state.distribute("p") }; runCurrent()
        assertEquals(1, repo.data.notes.size)
        screen.cancel(); runCurrent()
        assertTrue(caller.isCancelled)
        gate.complete(Unit); runCurrent()
        assertNull(state.current)
        assertEquals(1, state.projectNotes("p").size)
        assertFalse(state.busy)
        state.detachActionScope(backgroundScope)
    }
}
