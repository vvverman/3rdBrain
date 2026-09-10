package brain.studio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

@Composable
fun StudioApp(state: StudioState) {
    val scope = rememberCoroutineScope()
    DisposableEffect(state, scope) {
        state.attachActionScope(scope)
        onDispose { state.detachActionScope(scope) }
    }
    LaunchedEffect(Unit) { state.launch() }
    LaunchedEffect(Unit) { state.poll() }
    LaunchedEffect(state.editRevision) {
        if (state.editRevision > 0) { delay(400); state.autosave() }
    }
    StudioTheme(state.preferences.theme) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onSurface) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 430.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("3rdBrain", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, letterSpacing = (-.4).sp)
                        if (state.repository.simulated) Text(state.tr("demoBadge"), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.error != null -> Notice(state.tr(state.error!!), state.tr("ok")) { state.error = null }
                            state.confirmDelete -> Confirmation(state.tr("deleteTitle"), state.tr("deleteBody"), state.tr("delete"), state.tr("cancel"), { scope.launch { state.discard() } }, { state.confirmDelete = false })
                            state.confirmListenId != null -> Confirmation(state.tr("confirmListen"), state.tr("preserveRecording"), state.tr("stopAndPlay"), state.tr("cancel"), { scope.launch { state.confirmStopAndListen() } }, { state.confirmListenId = null })
                            state.editingProjectId != null -> ProjectEditor(state)
                            state.choosingProject -> DestinationScreen(state)
                            else -> when (state.tab) {
                                Tab.HOME -> HomeScreen(state)
                                Tab.PROJECTS -> ProjectsScreen(state)
                                Tab.SETTINGS -> SettingsScreen(state)
                            }
                        }
                    }
                    GlobalPlayer(state)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(Triple(Tab.HOME, "home", Glyph.HOME), Triple(Tab.PROJECTS, "projects", Glyph.FOLDER), Triple(Tab.SETTINGS, "settings", Glyph.SETTINGS)).forEach { (tab, key, icon) ->
                            val selected = state.tab == tab
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Tab) { state.navigate(tab) }
                                .semantics(mergeDescendants = true) { this.selected = selected }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Symbol(icon, Modifier.size(20.dp), if (selected) colors.onSurface else colors.onSurfaceVariant)
                                Spacer(Modifier.height(5.dp))
                                Text(state.tr(key), style = MaterialTheme.typography.labelSmall, color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Heading(title: String, back: (() -> Unit)? = null, backLabel: String = "", action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) { IconAction(backLabel, Glyph.BACK, back); Spacer(Modifier.width(12.dp)) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        action?.invoke()
    }
}

@Composable
private fun Notice(text: String, label: String, action: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Action(label, action, primary = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Confirmation(title: String, body: String, confirm: String, cancel: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Action(confirm, onConfirm, primary = true, modifier = Modifier.fillMaxWidth())
        QuietAction(cancel, onCancel, Modifier.align(Alignment.CenterHorizontally))
    }
}
