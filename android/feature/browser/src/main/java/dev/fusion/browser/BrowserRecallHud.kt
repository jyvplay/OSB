// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.fusion.browser.model.RecallMethodEscalationRouter

/**
 * Compact **Recall** automatic media recording HUD inside `BrowserScreen`:
 * switches folder delineation mode (`BY_URL_NAME`, `BY_PAGE_TITLE`, `BY_CUSTOM_TEMPLATE`),
 * toggles automatic capture for Images, Videos, and Music, and displays live 4-tier escalation telemetry.
 */
@Composable
fun BrowserRecallHud(
    recallEnabled: Boolean,
    onToggleRecall: (Boolean) -> Unit,
    delineationMode: String,
    onSelectDelineation: (String) -> Unit,
    customFolderPattern: String,
    onUpdateCustomPattern: (String) -> Unit,
    capturedImages: Int,
    capturedVideos: Int,
    capturedMusic: Int,
    activeTierLabel: String,
    lastArchivedFolder: String,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("browser-recall-hud"),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (recallEnabled) "Recall Auto-Archiver: ACTIVE ($activeTierLabel)"
                    else "Recall Auto-Archiver: PAUSED",
                    style = MaterialTheme.typography.labelLarge,
                )
                Switch(
                    checked = recallEnabled,
                    onCheckedChange = onToggleRecall,
                    modifier = Modifier.testTag("recall-toggle-switch"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "BY_URL_NAME" to "Folder: URL Name",
                    "BY_PAGE_TITLE" to "Folder: Page Title",
                    "BY_CUSTOM_TEMPLATE" to "Folder: Custom",
                ).forEach { (modeKey, label) ->
                    FilterChip(
                        selected = delineationMode == modeKey,
                        onClick = { onSelectDelineation(modeKey) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("recall-mode-$modeKey"),
                    )
                }
            }
            if (delineationMode == "BY_CUSTOM_TEMPLATE") {
                OutlinedTextField(
                    value = customFolderPattern,
                    onValueChange = { if (it.length <= 120) onUpdateCustomPattern(it) },
                    singleLine = true,
                    label = { Text("Custom Recall folder template ({custom}/{yyyy}-{mm}-{dd}/{domain}/{kind})") },
                    modifier = Modifier.fillMaxWidth().testTag("recall-custom-template"),
                )
            }
            Text(
                text = "Saved -> Images: $capturedImages • Videos: $capturedVideos • Music: $capturedMusic • Target: $lastArchivedFolder " +
                    "(6 methods: ${RecallMethodEscalationRouter.RecordingTier.entries.size} tiers incl. WebRTC RTP & ISO WARC/1.1; DRM/FLAG_SECURE strictly refused)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
