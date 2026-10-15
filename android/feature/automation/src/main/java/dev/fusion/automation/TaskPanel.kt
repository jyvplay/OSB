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

@Composable
fun TaskPanel(context: Context) {
    val repo=remember(context) { TaskRepository(File(context.noBackupFilesDir,"tasks-v1.bin")) }
    val scope=rememberCoroutineScope()
    var book by remember { mutableStateOf<TaskBook?>(null) }
    var title by remember { mutableStateOf("") };var date by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) };var blocked by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading local tasks…") }
    fun run(clearInput: Boolean=false, action: ()->TaskBook) {
        if(busy) return
        busy=true
        scope.launch {
            try {
                val result=withContext(Dispatchers.IO) { action() }
                book=result;blocked=false;status="Loaded/saved revision ${result.revision}. No reminder scheduled."
                if(clearInput) { title="";date="" }
            } catch(_:TaskConflict) { blocked=true;status="Tasks changed elsewhere. Reload before editing; your input is retained." }
            catch(_:Exception) { blocked=true;status="Load/save could not be confirmed. Data and input are not cleared. Reload to inspect; do not assume rollback." }
            finally { busy=false }
        }
    }
    LaunchedEffect(repo) { run { repo.load() } }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Local tasks",style=MaterialTheme.typography.titleSmall)
        Text("Plaintext on this device, excluded from Android backup. Up to128 tasks. Date labels only: no alarms, reminders, recurrence or calendar sync. Completion can be unchecked; deletion is not implemented.")
        Button(onClick={run {repo.load()}},enabled=!busy,modifier=Modifier.testTag("tasks-reload")) { Text("Reload tasks") }
        OutlinedTextField(title,{if(it.length<=256) title=it},Modifier.fillMaxWidth().testTag("tasks-title"),label={Text("Task title")},singleLine=true,enabled=!busy)
        OutlinedTextField(date,{if(it.length<=10) date=it},Modifier.fillMaxWidth().testTag("tasks-date"),label={Text("Due date yyyy-mm-dd (optional)")},singleLine=true,enabled=!busy)
        Button(enabled=!busy && !blocked && book!=null && book!!.tasks.size<128,modifier=Modifier.testTag("tasks-add"),onClick={
            val expected=book!!.revision;val text=title
            try {
                val due=LocalTask.date(date)
                LocalTask.validate(text,due)
                run(true) {repo.add(expected,text,due)}
            } catch(_:Exception) {status="Title/date refused. Use a nonblank title and a valid date in years0001..9999, or leave the date empty."}
        }) {Text("Add task")}
        Text(status,Modifier.testTag("tasks-status"))
        book?.tasks?.forEach { task ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(task.done,onCheckedChange={done -> val expected=book!!.revision;run {repo.mark(expected,task.id,done)}},enabled=!busy && !blocked,
                    modifier=Modifier.testTag("task-done-${task.id}").semantics {contentDescription="Completed: ${task.title}"})
                Column(Modifier.weight(1f)) {Text(task.title);Text(task.due?.let {"Date only: $it"} ?: "No due date")}
            }
        }
    }
}
