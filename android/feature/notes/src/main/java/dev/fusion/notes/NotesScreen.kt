package dev.fusion.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(model: NotesViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val ready=!state.busy && state.pending==null && !state.recoveryBlocked
    val revision=state.revision
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("notes-toolbar"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={ model.request(NoteAction.New) },enabled=ready) { Text("New note") }
            Button(onClick=model::save,enabled=ready && state.name.isNotBlank(),modifier=Modifier.testTag("notes-save")) { Text("Save note") }
            OutlinedButton(onClick=model::checkpointNow,enabled=ready,modifier=Modifier.testTag("notes-checkpoint")) { Text("Checkpoint draft") }
            OutlinedButton(onClick={ model.refresh() },enabled=ready) { Text("Refresh inventory") }
            if(revision!=null) OutlinedButton(onClick={ model.request(NoteAction.Delete(state.name,revision)) },enabled=ready) { Text("Move note to trash…") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("notes-body").semantics {paneTitle="Local Markdown notes"},
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Local Markdown • ${if(state.dirty) "Unsaved note draft" else "No unsaved changes"}")
        Text("256 KiB/note • 256 notes. Save explicitly. Draft recovery checkpoints are separate from Save note; unconfirmed recent edits can be lost. Saved-note wiki backlinks are explicit scans; no encryption, attachments, OCR or backup is claimed.")
        Text(state.recoveryStatus,Modifier.testTag("notes-recovery-status").semantics { liveRegion=LiveRegionMode.Polite })
        OutlinedTextField(state.name,model::name,Modifier.fillMaxWidth().testTag("notes-name"),enabled=ready && revision==null,singleLine=true,label={ Text("Note name (no path or extension)") })
        OutlinedTextField(state.text,model::edit,Modifier.fillMaxWidth().heightIn(min=180.dp,max=480.dp).testTag("notes-text"),enabled=ready,label={ Text("Markdown source") })
        if(state.busy) CircularProgressIndicator()
        Text(state.status,Modifier.semantics { liveRegion=LiveRegionMode.Polite })
        OutlinedButton(onClick=model::refreshLinks,enabled=ready,modifier=Modifier.testTag("notes-scan-links")) { Text("Scan saved-note backlinks") }
        Text("Links use exact [[note-name]] syntax. Case-sensitive; no aliases/Markdown URLs. Single-line inline code and fenced blocks are excluded; not a complete CommonMark parser. Scan limit:8 MiB and4096 references.")
        state.links?.let { graph ->
            if(state.name.isNotBlank() && runCatching { NoteFiles.validateName(state.name) }.isSuccess) {
                Text("Backlinks to ${state.name} (saved revisions only)")
                val incoming=graph.backlinks(state.name)
                if(incoming.isEmpty()) Text("No saved backlinks")
                incoming.forEach { name -> TextButton(onClick={ model.request(NoteAction.Open(name)) },enabled=ready) { Text("Backlink: $name") } }
                val missing=graph.unresolved(state.name)
                if(missing.isNotEmpty()) Text("Unresolved saved links (${missing.size}; first32 shown): ${missing.take(32).joinToString()}")
            }
        }
        OutlinedButton(onClick=model::refreshTrash,enabled=ready,modifier=Modifier.testTag("notes-trash-scan")) { Text("Refresh saved-note trash") }
        state.trash?.let { entries ->
            Text("Saved-note trash: ${entries.size}/256. No automatic purge. Restore refuses name conflicts.")
            entries.forEach { entry ->
                TextButton(onClick={ model.request(NoteAction.Restore(entry)) },enabled=ready,
                    modifier=Modifier.testTag("notes-restore-${entry.token}")) { Text("Restore ${entry.name} • ${entry.token.take(8)}") }
            }
        }
        Text("Saved notes (selecting another note prompts before discarding a draft)")
        state.names.forEach { name -> TextButton(onClick={ model.request(NoteAction.Open(name)) },enabled=ready,modifier=Modifier.fillMaxWidth()) { Text("Open note: $name") } }
    }
        }
    state.pending?.let { action ->
        val deleting=action is NoteAction.Delete
        AlertDialog(onDismissRequest=model::cancel,title={ Text(if(deleting) "Move saved note to trash?" else "Discard this memory draft?") },
            text={ Text(if(deleting) "Moves the saved revision to local trash; it can be restored without overwriting another note. Unsaved draft changes are discarded, not put in trash. A changed on-disk revision is refused. No automatic purge." else "Unsaved text will be discarded before opening or restoring the selected saved revision. Other saved notes are not overwritten.",Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())) },
            confirmButton={ TextButton(onClick=model::confirm) { Text(if(deleting) "Move saved revision" else "Discard draft") } },
            dismissButton={ TextButton(onClick=model::cancel) { Text("Keep editing") } })
    }
}
