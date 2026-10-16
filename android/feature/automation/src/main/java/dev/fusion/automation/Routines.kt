package dev.fusion.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Actual, explicit own-app dispatch. Session-only plan; old arbitrary drafts are untouched. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun AutomationScreen(context: android.content.Context, perform: ((LocalAction)->Boolean)? = null) {
    var tasks by remember { mutableStateOf(false) }
    var script by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Nothing executed.") }
    var running by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={tasks=!tasks},modifier=Modifier.testTag("tasks-mode")) { Text(if(tasks) "Back to action plans" else "Local tasks") }
        if(tasks) { TaskPanel(context);return@Column }
        Button(onClick={context.startActivity(android.content.Intent(context,CsvWorkbenchActivity::class.java))},modifier=Modifier.testTag("csv-workbench")) {Text("CSV selected-column worksheet")}
        Text("Local action plan",style=MaterialTheme.typography.titleSmall)
        Text("One action per line: open editor, open calculator, open notes, open files, open settings, or workspace 1 / 2 / 3. Up to16 actions; existing permission/experiment gates remain in force.")
        OutlinedTextField(script,{ if(it.length<=4096) script=it },Modifier.fillMaxWidth().testTag("routine-script"),label={ Text("Actions (session only)") },enabled=!running)
        Button(enabled=perform!=null && !running && script.isNotEmpty(),modifier=Modifier.testTag("routine-run"),onClick={
            running=true
            try {
                val plan=ActionPlan.parse(script)
                val result=plan.execute(requireNotNull(perform))
                status="Completed ${result.completed}/${result.total}. "+(result.failure ?: "Own-app actions executed.")
            } catch (_: Exception) { status="Plan refused; no actions started. Check grammar and limits." }
            finally { running=false }
        }) { Text("Execute local actions") }
        if(perform==null) Text("Host action dispatch unavailable; execution disabled.")
        Text(status,Modifier.testTag("routine-status"))
        Text("Not transactional: prior effects remain after refusal. No retries, triggers, timers or external control. Draft is not persisted. Old arbitrary-action drafts remain untouched and are not executed here.")
    }
}
