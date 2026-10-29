package dev.fusion.settingsui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.fusion.settings.SettingsGraph

/** ST-04/05: only controls wired to the host are interactive; planned presets are not toggles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(graph: SettingsGraph, probeText: String, density: String,
                   onDensity: (String) -> Unit, onShareReport: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<SettingsPatch?>(null) }
    var patchStatus by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        AppearanceControls(graph,"settings")
        HorizontalDivider(Modifier.padding(vertical=8.dp))
        Text("Window geometry density: $density")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("COMPACT", "BALANCED", "TOUCH").forEach { name ->
                OutlinedButton(onClick = { pending=SettingsPatch.plan(mapOf("density" to density),mapOf("density" to name));patchStatus="" }, enabled = name != density) { Text(name) }
            }
        }
        pending?.let { patch ->
            Text("Review density: ${patch.preview().single().before} → ${patch.preview().single().after}")
            Row {
                Button(onClick={
                    try {
                        val next=patch.apply(mapOf("density" to density))
                        onDensity(next.getValue("density"));pending=null
                        patchStatus="Change requested; verify the current density above. Not a durability receipt."
                    } catch(_:PreferenceConflict) { pending=null;patchStatus="Density changed since review; review again. Nothing applied." }
                },modifier=Modifier.testTag("settings-apply-density")) { Text("Apply reviewed density") }
                TextButton(onClick={ pending=null }) { Text("Cancel density change") }
            }
        }
        if(patchStatus.isNotEmpty()) Text(patchStatus)
        Text("Changes minimum window size/title controls only. Full spacing/font/density profiles are not implemented.")
        Text("Pilot keyboard bindings are fixed. Windows/Mac presets and conflict detection need S1; no nonfunctional preset toggle is offered.")
        val oemBatteryLink = remember {
            OemSettingsDeepLinkCatalog.resolveForOem(android.os.Build.MANUFACTURER ?: "aosp", OemSettingsDeepLinkCatalog.SystemTarget.BATTERY_UNRESTRICTED)
        }
        val permBaseline = remember {
            PermissionRiskAuditor.audit(setOf("POST_NOTIFICATIONS", "FOREGROUND_SERVICE_MEDIA_PROJECTION"))
        }
        Text(
            "OEM handoff: ${oemBatteryLink.primaryIntentAction} · fallback=${oemBatteryLink.fallbackIntentAction} · shell permission posture=${permBaseline.severity} (${permBaseline.riskScore}/100)",
            modifier = Modifier.padding(top = 6.dp).testTag("settings-oem-posture"),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(probeText, Modifier.padding(top = 8.dp))
        Text("Report excludes fingerprint, document text, paths, network addresses and notification contents. It includes OS/SoC/RAM/codec observations; review before sharing.")
        Button(onClick = onShareReport) { Text("Share redacted report…") }
        OutlinedTextField(query, { if(it.length<=256) query=it else patchStatus="Search query exceeds256 UTF16 code units; query unchanged." }, label = { Text("Search planned settings / aliases") },modifier=Modifier.testTag("settings-query"))
        graph.search(query).forEach { definition ->
            Text("${definition.title} (${definition.id}) — registry entry; only controls above are active", Modifier.padding(4.dp))
        }
    }
}
