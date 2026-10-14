package dev.fusion.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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

@Composable
fun NotesScreen(model: NotesViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val ready=!state.busy && state.pending==null
    val revision=state.revision
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).semantics { paneTitle="Local Markdown notes" },
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Local Markdown • ${if(state.dirty) "Unsaved memory draft" else "No unsaved changes"}")
        Text("256 KiB/note • 256 notes. Save explicitly. Draft survives Activity recreation but NOT process death. No encryption, backlinks, attachment support, autosave or backup is claimed.")
        OutlinedTextField(state.name,model::name,Modifier.fillMaxWidth().testTag("notes-name"),enabled=ready && revision==null,singleLine=true,label={ Text("Note name (no path or extension)") })
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={ model.request(NoteAction.New) },enabled=ready) { Text("New note") }
            Button(onClick=model::save,enabled=ready && state.name.isNotBlank(),modifier=Modifier.testTag("notes-save")) { Text("Save note") }
            OutlinedButton(onClick={ model.refresh() },enabled=ready) { Text("Refresh inventory") }
            if(revision!=null) OutlinedButton(onClick={ model.request(NoteAction.Delete(state.name,revision)) },enabled=ready) { Text("Delete note…") }
        }
        OutlinedTextField(state.text,model::edit,Modifier.fillMaxWidth().heightIn(min=180.dp,max=480.dp).testTag("notes-text"),enabled=ready,label={ Text("Markdown source") })
        if(state.busy) CircularProgressIndicator()
        Text(state.status,Modifier.semantics { liveRegion=LiveRegionMode.Polite })
        Text("Saved notes (selecting another note prompts before discarding a draft)")
        state.names.forEach { name -> TextButton(onClick={ model.request(NoteAction.Open(name)) },enabled=ready,modifier=Modifier.fillMaxWidth()) { Text("Open note: $name") } }
    }
    state.pending?.let { action ->
        val deleting=action is NoteAction.Delete
        AlertDialog(onDismissRequest=model::cancel,title={ Text(if(deleting) "Delete note permanently?" else "Discard this memory draft?") },
            text={ Text(if(deleting) "Deletes the saved note and discards its memory draft. There is no trash or undo. A changed on-disk revision will be refused." else "Unsaved text will be discarded. Previously saved files are not changed.") },
            confirmButton={ TextButton(onClick=model::confirm) { Text(if(deleting) "Delete permanently" else "Discard draft") } },
            dismissButton={ TextButton(onClick=model::cancel) { Text("Keep editing") } })
    }
}
