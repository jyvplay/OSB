package dev.fusion.settingsui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fusion.settings.SettingsGraph

/** ST-04/05: only controls wired to the host are interactive; planned presets are not toggles. */
@Composable
fun SettingsScreen(graph: SettingsGraph, probeText: String, density: String,
                   onDensity: (String) -> Unit, onShareReport: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Window geometry density: $density")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("COMPACT", "BALANCED", "TOUCH").forEach { name ->
                OutlinedButton(onClick = { onDensity(name) }, enabled = name != density) { Text(name) }
            }
        }
        Text("Changes minimum window size/title controls only. Full spacing/font/density profiles are not implemented.")
        Text("Pilot keyboard bindings are fixed. Windows/Mac presets and conflict detection need S1; no nonfunctional preset toggle is offered.")
        Text(probeText, Modifier.padding(top = 8.dp))
        Text("Report excludes fingerprint, document text, paths, network addresses and notification contents. It includes OS/SoC/RAM/codec observations; review before sharing.")
        Button(onClick = onShareReport) { Text("Share redacted report…") }
        OutlinedTextField(query, { query = it }, label = { Text("Search planned settings / aliases") })
        graph.search(query).forEach { definition ->
            Text("${definition.title} (${definition.id}) — registry entry; only controls above are active", Modifier.padding(4.dp))
        }
    }
}
