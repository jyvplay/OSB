package dev.fusion.automation

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskPanel(context: Context) {
    val repo=remember(context) { TaskRepository(File(context.noBackupFilesDir,"tasks-v1.bin")) }
    val scope=rememberCoroutineScope()
    var book by remember { mutableStateOf<TaskBook?>(null) }
    var title by remember { mutableStateOf("") };var date by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingRevision by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TaskScope.ACTIVE) }
    var order by remember { mutableStateOf(TaskOrder.INSERTION) }
    var busy by remember { mutableStateOf(false) };var blocked by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading local tasks…") }
    var undo by remember { mutableStateOf<TaskChange?>(null) }
    var mergePreview by remember { mutableStateOf<TaskMergePreview?>(null) }
    var pendingExport by remember { mutableStateOf<TaskBook?>(null) }
    var countsPreview by remember { mutableStateOf<TaskCounts?>(null) }
    var pendingCounts by remember { mutableStateOf<TaskCounts?>(null) }
    var pendingWorkbook by remember { mutableStateOf<TaskBook?>(null) }
    fun execute(clearInput: Boolean=false, keepUndoOnNoOp:Boolean=false, action: ()->TaskCommit) {
        if(busy) return
        busy=true
        scope.launch {
            try {
                val result=withContext(Dispatchers.IO) { action() }
                book=result.book;undo=result.undo ?: if(keepUndoOnNoOp) undo?.takeIf {it.matchesAfter(result.book)} else null;mergePreview=null;countsPreview=null;blocked=false;status="Loaded/saved revision ${result.book.revision}. No reminder scheduled."
                if(clearInput) { title="";date="";editingId=null }
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(_:TaskConflict) { blocked=true;status="Tasks changed elsewhere. Input retained. Reload, cancel this edit and reselect the task to review its current version." }
            catch(_:Exception) { blocked=true;status="Load/save could not be confirmed. Data and input are not cleared. Reload to inspect; do not assume rollback." }
            finally { busy=false }
        }
    }
    fun run(clearInput:Boolean=false,action:()->TaskBook) { execute(clearInput) {TaskCommit(action(),null)} }
    // [U] verify on API 34/36/37: picker grants, provider readback and recreation/cancellation.
    val exportPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri ->
        val snapshot=pendingExport;pendingExport=null
        if(uri==null) status="Export cancelled; no app write attempted."
        else if(snapshot==null) status="Export snapshot lost after recreation; created document may be empty. Start a new export."
        else {
            busy=true
            scope.launch {
                try {
                    val receipt=withContext(Dispatchers.IO) {TaskTransferIO.writeCreatedDocument(context.contentResolver,uri,snapshot)}
                    status="Export readback verified: revision ${receipt.revision}, ${receipt.bytes} bytes. Not a physical-durability guarantee."
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(_:Exception) {status="Export could not be confirmed; created document may be partial. Local tasks unchanged. Inspect before retrying."}
                finally {busy=false}
            }
        }
    }
    val workbookPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {uri ->
        val snapshot=pendingWorkbook;pendingWorkbook=null
        if(uri==null) status="Workbook export cancelled; no app write attempted."
        else if(snapshot==null) status="Workbook snapshot lost after recreation; created document may be empty."
        else {
            busy=true
            scope.launch {
                try {
                    val receipt=withContext(Dispatchers.IO) {TaskTransferIO.writeCreatedWorkbook(context.contentResolver,uri,snapshot)}
                    status="Literal XLSX report readback verified: ${receipt.bytes} bytes. Dates are text; not a task backup/import file or Office engine."
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(_:Exception) {status="Workbook export refused or unconfirmed; created document may be empty/partial. Tasks unchanged. Unsupported XML/escape-like text is not silently changed."}
                finally {busy=false}
            }
        }
    }
    val countsPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {uri ->
        val snapshot=pendingCounts;pendingCounts=null
        if(uri==null) status="Counts export cancelled; no app write attempted."
        else if(snapshot==null) status="Counts snapshot lost after recreation; created document may be empty."
        else {
            busy=true
            scope.launch {
                try {
                    val receipt=withContext(Dispatchers.IO) {TaskTransferIO.writeCreatedCounts(context.contentResolver,uri,snapshot)}
                    status="Counts-only XLSX readback verified: ${receipt.bytes} bytes. Titles, IDs, dates and revision omitted; counts/context can still identify you."
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(_:Exception) {status="Counts export unconfirmed; new document may be empty/partial. Local tasks unchanged."}
                finally {busy=false}
            }
        }
    }
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        val snapshot=book
        if(uri!=null && snapshot!=null) {
            busy=true
            scope.launch {
                try {
                    val preview=withContext(Dispatchers.IO) {TaskMergePreview.inspect(snapshot,TaskTransferIO.read(context.contentResolver,uri))}
                    mergePreview=preview;status="Import preview ready; no tasks changed. Review before confirming."
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(_:Exception) {mergePreview=null;status="Import refused: invalid/conflicting/over-limit data or unavailable provider. No tasks changed."}
                finally {busy=false}
            }
        } else status="Import cancelled or tasks not loaded; no tasks changed."
    }
    LaunchedEffect(repo) { run { repo.load() } }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Local tasks",style=MaterialTheme.typography.titleSmall)
        Text("Plaintext on this device, excluded from Android backup. Up to128 active and archived tasks combined. Archive is reversible and retains data; permanent deletion is not implemented. Date labels only: no reminders, recurrence or calendar sync. Saving a change upgrades old task data to FTS2; older app versions cannot read it. Loading alone does not upgrade.")
        Button(onClick={run {repo.load()}},enabled=!busy,modifier=Modifier.testTag("tasks-reload")) { Text("Reload tasks") }
        OutlinedTextField(title,{if(it.length<=256) title=it},Modifier.fillMaxWidth().testTag("tasks-title"),label={Text("Task title")},singleLine=true,enabled=!busy)
        OutlinedTextField(date,{if(it.length<=10) date=it},Modifier.fillMaxWidth().testTag("tasks-date"),label={Text("Due date yyyy-mm-dd (optional)")},singleLine=true,enabled=!busy)
        if(editingId!=null) {
            Text("Editing selected task at revision $editingRevision; ID and completion are preserved.")
            Button(onClick={editingId=null;title="";date=""},enabled=!busy,modifier=Modifier.testTag("tasks-cancel-edit")) {Text("Cancel edit")}
        }
        Button(enabled=!busy && !blocked && book!=null && (editingId!=null || book!!.tasks.size<128),modifier=Modifier.testTag("tasks-add"),onClick={
            val id=editingId;val expected=if(id==null) book!!.revision else editingRevision;val text=title
            try {
                val due=LocalTask.date(date)
                LocalTask.validate(text,due)
                if(id==null) run(true) {repo.add(expected,text,due)}
                else execute(clearInput=true,keepUndoOnNoOp=true) {repo.commitUndoable(expected) {it.edit(id,text,due)}}
            } catch(_:Exception) {status="Title/date refused. Use a nonblank title and a valid date in years0001..9999, or leave the date empty."}
        }) {Text(if(editingId==null) "Add task" else "Save task changes")}
        Text(status,Modifier.testTag("tasks-status"))
        undo?.let {change ->
            Text("One-step edit/import undo is session-only; another mutation or reload clears it. Undo adds a new revision, not a rollback.")
            Button(enabled=!busy && !blocked && editingId==null,onClick={run {repo.undo(change)}},modifier=Modifier.testTag("tasks-undo")) {Text("Undo last edit/import")}
        }
        Text("${book?.tasks?.count { !it.archived } ?: 0} active; ${book?.tasks?.count { it.archived } ?: 0} archived. Archived items still count toward128.")
        OutlinedTextField(query,{candidate ->
            try {TaskQuery.select(book ?: TaskBook(),candidate);query=candidate}
            catch(_:Exception) {status="Search refused: use up to256 Unicode units without control characters."}
        },Modifier.fillMaxWidth().testTag("tasks-query"),label={Text("Find title (literal, case-insensitive)")},singleLine=true)
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            TaskScope.entries.forEach { value ->
                OutlinedButton(onClick={filter=value},modifier=Modifier.testTag("tasks-scope-$value")) {Text((if(filter==value) "Selected: " else "")+value.name)}
            }
        }
        Button(onClick={order=if(order==TaskOrder.INSERTION) TaskOrder.DUE else TaskOrder.INSERTION},modifier=Modifier.testTag("tasks-order")) {Text("Order: $order")}
        val visible=book?.let {TaskQuery.select(it,query,filter,order)} ?: emptyList()
        if(book!=null && visible.isEmpty()) Text("No tasks match this view.")
        visible.forEach { task ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(task.done,onCheckedChange={done -> val expected=book!!.revision;run {repo.mark(expected,task.id,done)}},enabled=!busy && !blocked && editingId==null && !task.archived,
                    modifier=Modifier.testTag("task-done-${task.id}").semantics {contentDescription="Completed: ${task.title}"})
                Column(Modifier.weight(1f)) {
                    Text(task.title);Text(task.due?.let {"Date only: $it"} ?: "No due date")
                    if(!task.archived) TextButton(enabled=!busy && !blocked && editingId==null,onClick={
                        editingId=task.id;editingRevision=book!!.revision;title=task.title;date=task.due?.toString() ?: ""
                    },modifier=Modifier.testTag("task-edit-${task.id}")) {Text("Edit task")}
                    TextButton(enabled=!busy && !blocked && editingId==null,onClick={
                        val expected=book!!.revision;run {repo.archive(expected,task.id,!task.archived)}
                    },modifier=Modifier.testTag("task-archive-${task.id}")) {Text(if(task.archived) "Restore task" else "Archive task")}
                }
            }
        }
        Text("Manual task transfer: plaintext, including completed and archived records. Export creates a new FTS2 document; older app versions cannot read it. Import appends new IDs only, never replaces existing tasks or adopts the file's revision. Undoing an import removes its newly added records; the source file is untouched.")
        Button(enabled=!busy && !blocked && book!=null && editingId==null,onClick={
            pendingExport=book
            runCatching {exportPicker.launch("fusion-tasks-r${book!!.revision}.fts")}.onFailure {pendingExport=null;status="Document picker unavailable; no app write attempted."}
        },modifier=Modifier.testTag("tasks-export")) {Text("Export all tasks — new document")}
        Text("Optional XLSX report: Active and Archived sheets, literal cells only, dates as text. Not import-compatible or a full Office editor. Restricted writer refuses XML/escape-like text it cannot preserve; FTS remains available.")
        Button(enabled=!busy && !blocked && book!=null && editingId==null,onClick={
            pendingWorkbook=book
            runCatching {workbookPicker.launch("fusion-task-report-r${book!!.revision}.xlsx")}.onFailure {pendingWorkbook=null;status="Document picker unavailable; no workbook written."}
        },modifier=Modifier.testTag("tasks-export-workbook")) {Text("Export task report — new XLSX")}
        Text("Optional counts-only report: explicitly omits titles, IDs, dates, order and revision. Counts and sharing context may still identify people. Not anonymous, not a backup. Preview uses the loaded snapshot; reload first if another window changed tasks.")
        Button(enabled=!busy && !blocked && book!=null && editingId==null,onClick={countsPreview=TaskCounts.from(book!!)},modifier=Modifier.testTag("tasks-preview-counts")) {Text("Preview counts-only report")}
        countsPreview?.let {counts ->
            Text("Counts: active open ${counts.activeOpen}, active completed ${counts.activeDone}; archived open ${counts.archivedOpen}, archived completed ${counts.archivedDone}.")
            Button(enabled=!busy && !blocked && editingId==null,onClick={
                pendingCounts=counts
                runCatching {countsPicker.launch("fusion-task-counts.xlsx")}.onFailure {pendingCounts=null;status="Document picker unavailable; no counts written."}
            },modifier=Modifier.testTag("tasks-export-counts")) {Text("Export reviewed counts — new XLSX")}
            Button(enabled=!busy,onClick={countsPreview=null}) {Text("Discard counts preview")}
        }
        Button(enabled=!busy && !blocked && book!=null && editingId==null,onClick={
            mergePreview=null
            runCatching {importPicker.launch(arrayOf("*/*"))}.onFailure {status="Document picker unavailable; no tasks changed."}
        },modifier=Modifier.testTag("tasks-import")) {Text("Open task file for merge preview")}
        mergePreview?.let {preview ->
            Text("Preview: ${preview.added.size} new; ${preview.identical} identical. Source revision ${preview.sourceRevision} will NOT become local revision. Existing records stay unchanged.")
            preview.added.forEach {task -> Text("${task.id}: ${task.title}; date=${task.due ?: "none"}; done=${task.done}; archived=${task.archived}")}
            Button(enabled=!busy && !blocked && editingId==null,onClick={execute(keepUndoOnNoOp=true) {repo.merge(preview)}},modifier=Modifier.testTag("tasks-confirm-merge")) {Text("Confirm append-only merge")}
            Button(enabled=!busy,onClick={mergePreview=null;status="Preview discarded; no tasks changed."}) {Text("Discard merge preview")}
        }
    }
}
