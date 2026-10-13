package dev.fusion.settingsui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fusion.hw.HwProbe
import dev.fusion.perf.MemoryProbe
import dev.fusion.perf.ThermalProbe
import dev.fusion.settings.SettingsGraph

/** Real settings + probe. Density/shortcuts apply where the host reads them. */
@Composable
fun SettingsScreen(graph: SettingsGraph, probeText: String) {
    var q by remember { mutableStateOf("") }
    var density by remember { mutableStateOf(graph.get("density")) }
    var shortcuts by remember { mutableStateOf(graph.get("shortcuts")) }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("Search settings") })
        graph.search(q).forEach { d ->
            Text("${d.id} = ${graph.get(d.id)}  (${d.title})", Modifier.padding(4.dp))
        }
        Row {
            Button(onClick = {
                density = if (density == "balanced") "compact" else if (density == "compact") "touch" else "balanced"
                graph.set("density", density)
            }) { Text("Density: $density") }
            Button(onClick = {
                shortcuts = if (shortcuts == "hybrid") "windows" else if (shortcuts == "windows") "mac" else "hybrid"
                graph.set("shortcuts", shortcuts)
            }) { Text("Keys: $shortcuts") }
            Button(onClick = { msg = graph.undo()?.let { "Undid ${it.id}" } ?: "Nothing to undo" }) { Text("Undo") }
        }
        if (msg.isNotEmpty()) Text(msg)
        Text(probeText, Modifier.padding(top = 8.dp))
        Text("Thermal/Memory shown live in host where available.")
    }
}
