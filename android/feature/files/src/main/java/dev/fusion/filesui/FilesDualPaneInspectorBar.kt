// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.fusion.designsystem.CascadingContextMenuNode
import dev.fusion.designsystem.CrispHintedViewModeToolbar
import dev.fusion.designsystem.FusionContextMenuSubmenuHost
import dev.fusion.designsystem.FusionViewModeVectorIcons

/**
 * **Files Commander Dual-Pane View-Mode Synchronizer + Right-Sized Cascading Context-Menu Bar**
 * connecting `CrispHintedViewModeToolbar` (`List` / `Grid` / `Columns` bespoke vector logos),
 * `DualPaneViewModeSyncStore`, and `FusionContextMenuSubmenuHost` (`180..280 dp` primary +
 * `168..240 dp` secondary submenu)!
 */
@Composable
fun FilesDualPaneInspectorBar(
    leftPath: String,
    rightPath: String,
    viewportWidthDp: Double,
    onActionTriggered: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var syncPanes by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(FusionViewModeVectorIcons.ViewModeKind.COLUMNS) }
    var contextMenuOpen by remember { mutableStateOf(false) }

    val menuNodes = remember {
        listOf(
            CascadingContextMenuNode("open", "Open", "Enter"),
            CascadingContextMenuNode(
                id = "open_with",
                label = "Open With",
                children = listOf(
                    CascadingContextMenuNode("open_writer", "Fusion Writer"),
                    CascadingContextMenuNode("open_calc", "Fusion Calc (300 fns)"),
                    CascadingContextMenuNode("open_media", "Fusion Media Hub"),
                ),
            ),
            CascadingContextMenuNode("rename", "Rename", "F2"),
            CascadingContextMenuNode(
                id = "privacy_scrub",
                label = "Privacy & Tails Scrub",
                dividerBefore = true,
                children = listOf(
                    CascadingContextMenuNode("mat2_clean", "Scrub Metadata (.cleaned)"),
                    CascadingContextMenuNode("vault_encrypt", "Encrypt into Vault"),
                ),
            ),
            CascadingContextMenuNode("trash", "Move to Trash", "Del", dividerBefore = true),
        )
    }

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag("files-dual-pane-inspector-bar"),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CrispHintedViewModeToolbar(
                    activeMode = viewMode,
                    onModeChanged = { viewMode = it },
                    surfaceLabel = "files-commander",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = syncPanes,
                        onClick = { syncPanes = !syncPanes },
                        label = { Text("Sync Panes") },
                    )
                    Box {
                        OutlinedButton(
                            onClick = { contextMenuOpen = true },
                            modifier = Modifier.testTag("files-open-cascading-menu-btn"),
                        ) {
                            Text("Actions ▾")
                        }
                        FusionContextMenuSubmenuHost(
                            expanded = contextMenuOpen,
                            onDismiss = { contextMenuOpen = false },
                            viewportWidthDp = viewportWidthDp,
                            cursorXDp = (viewportWidthDp - 300.0).coerceAtLeast(16.0),
                            nodes = menuNodes,
                            onSelectLeaf = onActionTriggered,
                        )
                    }
                }
            }
            Text(
                text = "Left: $leftPath · Right: $rightPath · Mode: ${viewMode.designCodename}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
