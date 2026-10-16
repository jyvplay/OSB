package dev.fusion.automation

import android.content.Context
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
    fun run(clearInput: Boolean=false, action: ()->TaskBook) {
        if(busy) return
        busy=true
        scope.launch {
            try {
                val result=withContext(Dispatchers.IO) { action() }
                book=result;blocked=false;status="Loaded/saved revision ${result.revision}. No reminder scheduled."
                if(clearInput) { title="";date="";editingId=null }
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(_:TaskConflict) { blocked=true;status="Tasks changed elsewhere. Input retained. Reload, cancel this edit and reselect the task to review its current version." }
            catch(_:Exception) { blocked=true;status="Load/save could not be confirmed. Data and input are not cleared. Reload to inspect; do not assume rollback." }
            finally { busy=false }
        }
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
                run(true) {if(id==null) repo.add(expected,text,due) else repo.edit(expected,id,text,due)}
            } catch(_:Exception) {status="Title/date refused. Use a nonblank title and a valid date in years0001..9999, or leave the date empty."}
        }) {Text(if(editingId==null) "Add task" else "Save task changes")}
        Text(status,Modifier.testTag("tasks-status"))
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
    }
}
