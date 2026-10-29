// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.fusion.designsystem.CrispHintedViewModeToolbar
import dev.fusion.designsystem.FusionViewModeVectorIcons

/**
 * **Encrypted Vault `List` / `Grid` / `Columns` 3-Mode Secret Browser** bringing our original
 * pixel-hinted `List` (`Spine-Ledger`), `Grid` (`Aperture-Mosaic`), and `Columns` (`Cascading
 * Miller-Triptych`) vector logos directly into `VaultScreen.kt`!
 */
@Composable
fun VaultColumnsSecretBrowser(
    secrets: List<VaultItemViewModeOrganizer.VaultSecretStub>,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(FusionViewModeVectorIcons.ViewModeKind.LIST) }
    val columns = remember(secrets) {
        VaultItemViewModeOrganizer.organizeColumns(secrets, selectedCategory = null, selectedFolder = null)
    }

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag("vault-columns-secret-browser"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vault Items (SLIP-39 + Duress Protected)", style = MaterialTheme.typography.titleSmall)
                CrispHintedViewModeToolbar(
                    activeMode = mode,
                    onModeChanged = { mode = it },
                    surfaceLabel = "vault",
                )
            }
            when (mode) {
                FusionViewModeVectorIcons.ViewModeKind.LIST -> {
                    secrets.forEach { s ->
                        Text("${s.label} · ${s.category}/${s.folder} · ${s.maskedPreview}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                FusionViewModeVectorIcons.ViewModeKind.GRID -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        secrets.take(3).forEach { s ->
                            Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.small) {
                                Text("${s.label}\n${s.maskedPreview}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                FusionViewModeVectorIcons.ViewModeKind.COLUMNS -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Categories", style = MaterialTheme.typography.labelSmall)
                            columns.categories.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Folders", style = MaterialTheme.typography.labelSmall)
                            columns.folders.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Secrets (${columns.items.size})", style = MaterialTheme.typography.labelSmall)
                            columns.items.forEach { Text("${it.label} (${it.maskedPreview})", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
