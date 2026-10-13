package dev.fusion.automation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Routine(val name: String, val action: String)

/**
 * Local routines with durable SharedPreferences storage.
 *
 * Honest scope: create, list, delete, and dry-run named actions. There is no
 * scheduler, no background trigger, no AppFunctions exposure and no privileged
 * execution. A dry-run prints what WOULD happen and changes nothing.
 */
class RoutineStore(context: Context) {
    private val prefs = context.getSharedPreferences("fusion_routines", Context.MODE_PRIVATE)

    fun load(): List<Routine> = runCatching {
        val raw = prefs.getString("routines", "[]") ?: "[]"
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Routine(o.getString("name"), o.getString("action"))
        }
    }.getOrDefault(emptyList())

    fun saveAll(routines: List<Routine>) {
        val arr = JSONArray()
        routines.forEach { r ->
            arr.put(JSONObject().apply { put("name", r.name); put("action", r.action) })
        }
        prefs.edit().putString("routines", arr.toString()).apply()
    }
}

@Composable
fun AutomationScreen(context: Context) {
    val store = remember(context) { RoutineStore(context) }
    val routines = remember { mutableStateListOf<Routine>().apply { addAll(store.load()) } }
    var name by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("No dry-run yet.") }

    fun persist() = store.saveAll(routines.toList())

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Automation — local routines", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Routine name") }, singleLine = true)
        OutlinedTextField(action, { action = it }, Modifier.fillMaxWidth(), label = { Text("Action, e.g. open workspace 2") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (name.isNotBlank() && action.isNotBlank()) {
                    routines.add(Routine(name.trim().take(60), action.trim().take(200)))
                    name = ""; action = ""
                    persist()
                    output = "Saved locally. Nothing executed."
                }
            }) { Text("Add") }
            OutlinedButton(onClick = {
                output = if (routines.isEmpty()) "Nothing to dry-run."
                else routines.joinToString("\n") { "[DRY] ${it.name}: ${it.action} (no changes made)" }
            }) { Text("Dry-run all") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(routines, key = { it.name + "|" + it.action }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("• ${r.name} → ${r.action}", Modifier.weight(1f))
                    OutlinedButton(onClick = { routines.remove(r); persist() }) { Text("Delete") }
                }
            }
        }
        Text(output)
        Text(
            "Durable on this device only. No timers, triggers, assistants or cross-app control.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
