// SPDX-License-Identifier: Apache-2.0
package dev.fusion.quickpanel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fusion.settings.Appearance
import dev.fusion.settings.DesktopScene

@Composable
fun QuickPanelSheet(
    snapshot: QuickPanelSnapshot,
    onAppearanceChange: (Appearance) -> Unit,
    onAmnesicWipe: () -> Unit,
    scrollable: Boolean = true
) {
    val containerModifier = if (scrollable) {
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
    }
    Column(
        containerModifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Studio Mode & Palette", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        StudioPreset.entries.forEach { preset ->
            val selected = snapshot.appearance.palette == preset.appearance.palette &&
                snapshot.appearance.layout == preset.appearance.layout
            OutlinedButton(
                onClick = { onAppearanceChange(snapshot.applyPreset(preset).appearance) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("qp-preset-${preset.id}")
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        (if (selected) "✓ " else "") + preset.title,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(preset.subtitle, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Divider()
        Text("Workspace Geometry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = snapshot.appearance.scene == DesktopScene.TABLETOP,
                onClick = { onAppearanceChange(snapshot.appearance.copy(scene = DesktopScene.TABLETOP)) },
                label = { Text("3D Slanted Tabletop (83%)") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("qp-scene-tabletop")
            )
            FilterChip(
                selected = snapshot.appearance.scene == DesktopScene.FLAT,
                onClick = { onAppearanceChange(snapshot.appearance.copy(scene = DesktopScene.FLAT)) },
                label = { Text("Flat Desktop") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("qp-scene-flat")
            )
        }
        Divider()
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Amnesic Session Posture", fontWeight = FontWeight.SemiBold)
            Text(
                "Active session consents: ${snapshot.activeConsentCount} · Open windows: ${snapshot.openWindowCount} · Wipe epoch: ${snapshot.amnesicWipeGeneration}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("qp-posture-summary")
            )
            Button(
                onClick = onAmnesicWipe,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("qp-amnesic-wipe")
            ) {
                Text("Zeroize session secrets & revoke consents now")
            }
        }
    }
}
