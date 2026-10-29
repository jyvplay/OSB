package dev.fusion.alerts

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fusion.storage.AlertRow
import dev.fusion.storage.AlertStore
import dev.fusion.storage.FusionDb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(context: Context, onOpenListenerSettings: () -> Unit) {
    val store = remember { AlertStore(FusionDb(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    val enabled = remember(refresh) { FusionNotificationListener.isEnabled(context) }
    val rows: List<AlertRow> = remember(refresh, enabled) {
        if (enabled) runCatching { store.recent() }.getOrDefault(emptyList()) else emptyList()
    }
    val diagSummary = remember(rows, enabled) {
        val dispositions = rows.map {
            NotificationDiagnosticLedger.classifyNotification(
                isVisibilitySecret = false,
                isOngoing = false,
                isGroupSummary = false,
                suppressedByFocusProfile = false,
            )
        }
        NotificationDiagnosticLedger.summarizeDispositions(dispositions, enabled)
    }
    val fmt = remember { SimpleDateFormat("MMM d HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!enabled) {
            Text("Notification access is OFF. Nothing is being read or stored.")
            Button(onClick = onOpenListenerSettings) { Text("Open notification access settings") }
            Text(
                "Grant 'Fusion Alerts', then press Refresh. Secret notifications are never stored; " +
                    "the log is wiped if access is revoked.",
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { refresh++ }) { Text("Refresh") }
                OutlinedButton(onClick = { store.clear(); refresh++ }) { Text("Clear log") }
                OutlinedButton(onClick = onOpenListenerSettings) { Text("Access") }
            }
            val storedCnt = diagSummary.countsByDisposition[NotificationDiagnosticLedger.Disposition.STORED_IN_CENTER] ?: 0
            Text("$storedCnt stored (${diagSummary.totalEvaluated} evaluated, 7-day retention, ongoing + group summaries + VISIBILITY_SECRET skipped)")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.key }) { r ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(
                        "${r.pkg}  ·  ${fmt.format(Date(r.posted))}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(r.title.ifBlank { "(no title)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.body.isNotBlank()) {
                        Text(r.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (enabled && rows.isEmpty()) {
            Text("No stored alerts yet. This is a real empty result, not a placeholder.")
        }
    }
}
