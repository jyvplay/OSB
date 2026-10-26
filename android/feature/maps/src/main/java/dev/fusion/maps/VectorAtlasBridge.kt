// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * R26 AUTHORED, NOT EXECUTED IN THIS SANDBOX. Optional on-device vector-map integration.
 *
 * Presence-gated by design: this compiles with ZERO new dependencies and binds the
 * LGPLv3 MapsForge library (org.mapsforge.map.android.view.MapView) only if the host build
 * includes it. Absence is disclosed, never faked. MapsForge renders OpenStreetMap vector
 * data fully offline; it is not Google Maps, routing, traffic or navigation.
 */
object VectorAtlasBridge {
    const val MAP_VIEW_CLASS = "org.mapsforge.map.android.view.MapView"
    sealed interface Status {
        data object Absent : Status
        data class Available(val version: String?) : Status
        data class Error(val reason: String) : Status
    }

    fun probe(): Status = try {
        val loaded = Class.forName(MAP_VIEW_CLASS)
        Status.Available(loaded.`package`?.implementationVersion)
    } catch (_: ClassNotFoundException) {
        Status.Absent
    } catch (t: Throwable) {
        Status.Error(t.message?.take(160) ?: "probe failed")
    }

    fun disclosure(status: Status): String = when (status) {
        is Status.Available -> "Vector engine present${status.version?.let { " ($it)" }.orEmpty()}. " +
            "Offline OpenStreetMap vector rendering only; no routing, traffic, search-by-name or live location."
        is Status.Absent -> "Vector engine not bundled. The built-in Natural Earth offline atlas below remains " +
            "available. No map tiles are fetched and no position is uploaded."
        is Status.Error -> "Vector engine probe failed (${status.reason}). Treated as absent; offline atlas remains."
    }
}

@Composable
fun VectorAtlasPanel(onOpenAtlas: () -> Unit) {
    var status by remember { mutableStateOf<VectorAtlasBridge.Status?>(null) }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vector maps (optional)", style = MaterialTheme.typography.titleSmall)
        val current = status
        if (current == null) {
            Text("Check whether this build bundles the offline vector engine. No network is used.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { status = VectorAtlasBridge.probe() },
                modifier = Modifier.heightIn(min = 48.dp).testTag("vector-probe")) { Text("Check engine") }
        } else {
            Text(VectorAtlasBridge.disclosure(current), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("vector-status"))
            OutlinedButton(onClick = onOpenAtlas,
                modifier = Modifier.heightIn(min = 48.dp).testTag("vector-atlas")) { Text("Open offline atlas") }
        }
    }
}
