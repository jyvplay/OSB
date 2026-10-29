package dev.fusion.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** PR-01 / FF-15 / FM-18. Real SAF I/O is owned by an Activity-scoped ViewModel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(model: EditorViewModel, onSaveCopy: (CopyPickerRequest) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val session = state.session
    val pendingCopy = session.pendingCopy
    val active = session.documents.find { it.id == session.selectedId }
    val dirty = remember(active?.content, active?.savedDigest) { active?.dirty == true }
    var toolsOpen by remember { mutableStateOf(false) }
    val editorFocus = remember { FocusRequester() }
    LaunchedEffect(toolsOpen,active?.id) { if(!toolsOpen && active != null) editorFocus.requestFocus() }
    var copyConfirmId by remember { mutableStateOf<String?>(null) }
    var retryConfirm by remember { mutableStateOf(false) }
    var retryConfirmId by remember { mutableStateOf<String?>(null) }
    var abandoningCopy by remember { mutableStateOf(false) }
    var abandoningCopyId by remember { mutableStateOf<String?>(null) }
    var closingId by remember { mutableStateOf<String?>(null) }
    var openingEncoding by rememberSaveable { mutableStateOf<dev.fusion.documents.TextEncoding?>(null) }
    var pickerEncoding by rememberSaveable { mutableStateOf<dev.fusion.documents.TextEncoding?>(null) }
    var encodingMenu by remember { mutableStateOf(false) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.open(uri,encoding=pickerEncoding)
    }
    val ready = !state.loading && !state.recoveryBlocked && !state.busy && !state.toolsBusy

    Column(Modifier.fillMaxSize().padding(12.dp).semantics { paneTitle = "Text editor" }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || event.isAltPressed || !ready) false
            else when (event.key) {
                Key.Z -> { model.undo(redo=event.isShiftPressed); true }
                Key.Y -> { model.undo(redo=true); true }
                Key.F, Key.H, Key.G -> if(active != null) { toolsOpen=true; true } else false
                Key.S -> if(active != null && pendingCopy==null) { copyConfirmId=active.id; true } else false
                Key.O -> { pickerEncoding=openingEncoding; openDocument.launch(arrayOf("text/*", "application/json")); true }
                Key.N -> { model.newDocument(); true }
                else -> false
            }
        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Keep actions outside the focused document's scroll/bring-into-view region.
        // Bounded independent scrolling keeps a tall toolbar reachable at large font sizes.
        FlowRow(Modifier.weight(0.4f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            OutlinedButton(enabled = ready, onClick = model::newDocument) { Text("New") }
            OutlinedButton(enabled = ready, onClick = { pickerEncoding=openingEncoding; openDocument.launch(arrayOf("text/*", "application/json")) }) { Text("Open text") }
            Button(enabled = ready && active != null && pendingCopy == null,
                onClick = { copyConfirmId=active?.id }) { Text("Save a Copy") }
            OutlinedButton(enabled = ready && active != null && pendingCopy?.documentId != active?.id,
                onClick = {
                    active?.let { if (dirty) closingId = it.id else model.closeDocument(it.id) }
                }) { Text("Close tab") }
        }
        Column(Modifier.weight(0.6f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth()) {
            session.documents.forEach { doc ->
                val docDirty = remember(doc.content, doc.savedDigest) { doc.dirty }
                TextButton(enabled = ready, onClick = { model.select(doc.id) },modifier=Modifier.testTag("editor-tab")) {
                    Text((if (doc.id == active?.id) "Selected: " else "") + doc.name + if (docDirty) " • Unsaved" else "")
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth()) {
            OutlinedButton(enabled=ready && (state.buffers[active?.id]?.undoDepth ?: 0)>0,onClick={ model.undo() },modifier=Modifier.testTag("editor-undo")) { Text("Undo") }
            OutlinedButton(enabled=ready && (state.buffers[active?.id]?.redoDepth ?: 0)>0,onClick={ model.undo(redo=true) },modifier=Modifier.testTag("editor-redo")) { Text("Redo") }
            OutlinedButton(enabled=ready && active != null,onClick={ toolsOpen=true }) { Text("Find / Replace / Go to") }
        }
        if (state.loading || state.busy || state.toolsBusy) CircularProgressIndicator()
        Text(state.status, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (pendingCopy != null) {
            Text("Frozen copy: ${pendingCopy.name}. State: ${pendingCopy.stage}. Retry invalidates older results and keeps the original frozen payload. Inspect any partial destination first; no in-place replay or automatic deletion.")
            FlowRow(Modifier.fillMaxWidth()) {
                OutlinedButton(enabled = ready, onClick = { retryConfirmId=pendingCopy?.requestId; retryConfirm=true }) { Text("Retry with a new picker") }
                TextButton(enabled = ready, onClick = { abandoningCopyId=pendingCopy?.requestId; abandoningCopy=true }) { Text("Abandon pending attempt") }
            }
        }
        if (active != null) {
            val draftText = state.fields.getValue(active.id)
            val metrics = remember(draftText) { ReadabilityMetrics.analyze(draftText) }
            val mathCount = remember(draftText) { Regex("\\$[^$\\n]{1,120}\\$").findAll(draftText).count() }
            val citeCount = remember(draftText) { Regex("\\[@[A-Za-z0-9_:-]+]").findAll(draftText).count() }
            Text(
                "Writer inspector: ${metrics.wordCount} words • ${metrics.sentenceCount} sentences • " +
                    "FK grade ${"%.1f".format(metrics.fleschKincaidGrade)} • ~${metrics.estimatedReadingSeconds}s read" +
                    (if (mathCount > 0) " • $mathCount LaTeX spans" else "") +
                    (if (citeCount > 0) " • $citeCount [@citations]" else "") +
                    (if (metrics.repeatedAdjacentWords.isNotEmpty()) " • duplicate: ${metrics.repeatedAdjacentWords.joinToString()}" else ""),
                modifier = Modifier.testTag("editor-writer-inspector"),
            )
            OutlinedTextField(value = draftText, onValueChange = { model.edit(active.id, it) },
                modifier = Modifier.fillMaxWidth().heightIn(min=180.dp,max=480.dp).focusRequester(editorFocus).testTag("editor-content"), enabled = !state.recoveryBlocked,
                label = { Text(active.name + if (dirty) " — Unsaved changes" else " — No unsaved changes") })
        }
        session.copyReceipts.firstOrNull()?.let { receipt ->
            Text("Last copy receipt: ${receipt.outcome} · ${receipt.destinationUri?.take(180) ?: "No destination returned"}. Readback is not cloud sync or power-loss durability. Last 16 receipts retained locally.")
        }
        Box {
            OutlinedButton(onClick={ encodingMenu=true },enabled=ready) { Text("Open encoding: ${openingEncoding?.label ?: "BOM detection / UTF-8"}") }
            DropdownMenu(expanded=encodingMenu,onDismissRequest={ encodingMenu=false },modifier=Modifier.widthIn(min=180.dp,max=280.dp)) {
                DropdownMenuItem(text={ Text("BOM detection / strict UTF-8") },onClick={ openingEncoding=null;encodingMenu=false })
                dev.fusion.documents.TextEncoding.entries.forEach { encoding ->
                    DropdownMenuItem(text={ Text(encoding.label) },onClick={ openingEncoding=encoding;encodingMenu=false })
                }
            }
        }
        active?.let { Text("Draft encoding: ${it.encoding.label}. Unrepresentable export characters are refused, not replaced.") }
        val undoDepth = state.buffers[active?.id]?.undoDepth ?: 0
        if (undoDepth > 0 && active != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                dev.fusion.designsystem.StackedUndoFanOverlayBar(
                    itemsNewestFirst = (1..minOf(undoDepth, 3)).map { step ->
                        dev.fusion.designsystem.InteractiveUndoCardModel(
                            id = "${active.id}#u$step",
                            summary = if (step == 1) "Edited ${active.name} ($undoDepth undo step(s))" else "Prior edit #$step in ${active.name}",
                            ttlProgress = (1.0f - (step - 1) * 0.25f).coerceIn(0.25f, 1.0f),
                            canUndo = step == 1,
                        )
                    },
                    viewportWidthDp = 960.0,
                    onUndoItem = { model.undo() },
                    onDismissAll = {},
                )
            }
        }
        val selMenuCount = remember(active?.id) { EditorSelectionContextMenuPlanner.planForSelection(true, true, false).size }
        Text("Strict UTF-8 / UTF-16 / explicit Windows-1252 • 2 MiB/document • 8 tabs • Selection menu=$selMenuCount right-sized verbs. Local recovery is not a backup or an external save. SAF atomic replacement is unavailable; use Save a Copy. Ctrl+O/N/S, Ctrl+Z/Shift+Z/Y, Ctrl+F/H/G. Undo is bounded and is not restored after process death.")
        }
    }
    if(toolsOpen) EditorTools(model,onDismiss={ model.cancelTools(); toolsOpen=false })
    copyConfirmId?.let { id -> CopyAdmissionDialog("Save a Copy to a new file?",
        onDismiss={ copyConfirmId=null },onConfirm={ copyConfirmId=null; model.prepareCopy(id,onSaveCopy) }) }
    if(retryConfirm) CopyAdmissionDialog("Retry the original frozen payload?",
        onDismiss={ retryConfirm=false },onConfirm={ retryConfirm=false; model.retryCopy(retryConfirmId,onSaveCopy) })
    if(abandoningCopy) AlertDialog(onDismissRequest={ abandoningCopy=false },title={ Text("Abandon this copy attempt?") },
        text={ Text("Keeps the current draft, but retires this frozen save payload. A late result for this attempt will not authorize a newer save. Any partial destination stays for you to inspect; it is not deleted.") },
        confirmButton={ TextButton(onClick={ model.cancelCopy(abandoningCopyId); abandoningCopy=false }) { Text("Abandon attempt") } },
        dismissButton={ TextButton(onClick={ abandoningCopy=false }) { Text("Keep pending attempt") } })
    closingId?.let { id ->
        AlertDialog(onDismissRequest = { closingId = null }, title = { Text("Discard unsaved changes?") },
            text = { Text("This removes this tab and its local recovery draft. The source document and previously saved copies are not changed.",Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { model.closeDocument(id, discard = true); closingId = null }) { Text("Discard draft") } },
            dismissButton = { TextButton(onClick = { closingId = null }) { Text("Keep editing") } })
    }
}

@Composable
private fun CopyAdmissionDialog(title: String, onDismiss: ()->Unit, onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={ Text(title) },
        text={ Text("Select a newly created destination. Android's Create Document action may return an existing item. Known aliases and nonempty/unreadable targets are refused, but provider identity, freshness and concurrent changes cannot be guaranteed. Use disposable data until provider acceptance passes. Retry keeps the original frozen payload, not newer edits.") },
        confirmButton={ TextButton(onClick=onConfirm) { Text("Choose a new destination") } },
        dismissButton={ TextButton(onClick=onDismiss) { Text("Keep draft unchanged") } })
}
