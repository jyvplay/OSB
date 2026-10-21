// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * R26 AUTHORED, NOT EXECUTED IN THIS SANDBOX. Explicit user-consent handoff to the
 * separate Orbot app (org.torproject.android) for Tor routing.
 *
 * Fusion routes no traffic itself and claims no anonymity from this panel: it only (a)
 * reports whether Orbot is installed, (b) opens Orbot so the USER grants VPN consent and
 * observes bootstrap there, and (c) discloses what remains unproven. An installed Orbot
 * icon is not a Tor circuit.
 */
object OrbotHandoff {
    const val ORBOT_PACKAGE = "org.torproject.android"
    enum class Readiness { INSTALLED, MISSING, UNKNOWN }

    fun readiness(manager: PackageManager): Readiness = try {
        manager.getPackageInfo(ORBOT_PACKAGE, 0)
        Readiness.INSTALLED
    } catch (_: PackageManager.NameNotFoundException) {
        Readiness.MISSING
    } catch (_: Exception) {
        Readiness.UNKNOWN
    }

    fun disclosure(state: Readiness): String = when (state) {
        Readiness.INSTALLED -> "Orbot is installed. Open it, grant VPN consent there, and wait for its own " +
            "\"Connected\" bootstrap indicator. Until Orbot itself reports a circuit, treat traffic as non-anonymous."
        Readiness.MISSING -> "Orbot is not installed, so no Tor routing exists. Install Orbot from a source you " +
            "trust (F-Droid/Play) if you need Tor; Fusion alone is not anonymous."
        Readiness.UNKNOWN -> "Orbot presence is UNKNOWN. Absence of evidence is not anonymity; treat traffic as non-anonymous."
    }
}

@Composable
fun OrbotHandoffPanel() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<OrbotHandoff.Readiness?>(null) }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tor routing (separate Orbot app)", style = MaterialTheme.typography.titleSmall)
        val current = state
        if (current == null) {
            Text("Fusion never routes traffic itself. Check for Orbot, then continue inside Orbot.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { state = OrbotHandoff.readiness(context.packageManager) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("orbot-check")) { Text("Check for Orbot") }
        } else {
            Text(OrbotHandoff.disclosure(current), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("orbot-status"))
            if (current == OrbotHandoff.Readiness.INSTALLED) {
                OutlinedButton(onClick = {
                    context.packageManager.getLaunchIntentForPackage(OrbotHandoff.ORBOT_PACKAGE)?.let { context.startActivity(it) }
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("orbot-open")) { Text("Open Orbot") }
            }
        }
    }
}
