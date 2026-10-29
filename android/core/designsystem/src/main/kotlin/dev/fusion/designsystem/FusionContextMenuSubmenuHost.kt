// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * **Right-Sized Primary (`180..280 dp`) + Cascading Secondary Submenu (`168..240 dp`) Popup Host**.
 *
 * Guarantees desktop/tablet/mobile right-click menus stay strictly compact (never full-width) and
 * flips secondary submenus to the left of the primary menu whenever the right viewport edge is near!
 */
@Composable
fun FusionContextMenuSubmenuHost(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewportWidthDp: Double,
    cursorXDp: Double,
    nodes: List<CascadingContextMenuNode>,
    onSelectLeaf: (String) -> Unit,
) {
    var activeSubmenuParentId by remember(expanded) { mutableStateOf<String?>(null) }

    val primaryWidthDp = remember(viewportWidthDp, nodes) {
        val longest = nodes.maxOfOrNull { it.label.length + it.shortcut.length + 4 } ?: 18
        val cap = if (viewportWidthDp >= 600.0) minOf(280.0, viewportWidthDp * 0.34) else minOf(240.0, viewportWidthDp * 0.68)
        (longest * 7.4 + 44.0).coerceIn(minOf(180.0, cap), cap)
    }

    Box {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                activeSubmenuParentId = null
                onDismiss()
            },
            modifier = Modifier
                .width(primaryWidthDp.dp)
                .widthIn(min = 180.dp, max = 280.dp)
                .testTag("fusion-cascading-primary-menu"),
        ) {
            nodes.forEachIndexed { idx, node ->
                if (idx > 0 && node.dividerBefore) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
                val hasSub = node.children.isNotEmpty()
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 38.dp).testTag("cascade-item-${node.id}"),
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = node.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (hasSub) "▸" else node.shortcut,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        if (hasSub) {
                            activeSubmenuParentId = if (activeSubmenuParentId == node.id) null else node.id
                        } else {
                            activeSubmenuParentId = null
                            onDismiss()
                            onSelectLeaf(node.id)
                        }
                    },
                )
            }
        }

        val activeParent = nodes.firstOrNull { it.id == activeSubmenuParentId && it.children.isNotEmpty() }
        if (expanded && activeParent != null) {
            val subWidthDp = remember(viewportWidthDp, activeParent) {
                val longest = activeParent.children.maxOfOrNull { it.label.length + it.shortcut.length + 3 } ?: 16
                val cap = minOf(240.0, viewportWidthDp * 0.28).coerceAtLeast(168.0)
                (longest * 7.2 + 40.0).coerceIn(168.0, cap)
            }
            val cascadeLeft = (cursorXDp + primaryWidthDp + subWidthDp > viewportWidthDp - 8.0)
            val horizOffset = if (cascadeLeft) (-subWidthDp + 4.0).dp else (primaryWidthDp - 4.0).dp

            DropdownMenu(
                expanded = true,
                onDismissRequest = { activeSubmenuParentId = null },
                offset = DpOffset(horizOffset, 8.dp),
                modifier = Modifier
                    .width(subWidthDp.dp)
                    .widthIn(min = 168.dp, max = 240.dp)
                    .testTag("fusion-cascading-submenu-${activeParent.id}"),
            ) {
                activeParent.children.forEach { child ->
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 36.dp).testTag("submenu-leaf-${child.id}"),
                        text = {
                            Text(
                                text = child.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            activeSubmenuParentId = null
                            onDismiss()
                            onSelectLeaf(child.id)
                        },
                    )
                }
            }
        }
    }
}

data class CascadingContextMenuNode(
    val id: String,
    val label: String,
    val shortcut: String = "",
    val dividerBefore: Boolean = false,
    val children: List<CascadingContextMenuNode> = emptyList(),
)
