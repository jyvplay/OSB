// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Right-sized desktop/tablet/mobile **Right-Click Context Menu Popup** bounded strictly to
 * `180.dp .. 280.dp` (and `<= 34%` of desktop viewport width), never stretching across the
 * full width of the page!
 */
@Composable
fun FusionContextMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewportWidthDp: Double,
    items: List<ContextMenuEntrySpec>,
    anchorOffsetDp: DpOffset = DpOffset(0.dp, 0.dp),
    onSelect: (String) -> Unit,
) {
    val targetWidthDp = remember(viewportWidthDp, items) {
        val longest = items.maxOfOrNull { it.label.length + it.shortcut.length + 3 } ?: 16
        val cap = if (viewportWidthDp >= 600.0) minOf(280.0, viewportWidthDp * 0.34) else minOf(240.0, viewportWidthDp * 0.68)
        (longest * 7.4 + 44.0).coerceIn(minOf(180.0, cap), cap)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = anchorOffsetDp,
        modifier = Modifier
            .width(targetWidthDp.dp)
            .widthIn(min = 180.dp, max = 280.dp)
            .testTag("fusion-right-sized-context-menu"),
    ) {
        items.forEachIndexed { idx, entry ->
            if (idx > 0 && entry.sectionBreakBefore) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 38.dp).testTag("ctx-item-${entry.id}"),
                text = {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = entry.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (entry.shortcut.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = entry.shortcut,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                enabled = entry.enabled,
                onClick = {
                    onDismiss()
                    onSelect(entry.id)
                },
            )
        }
    }
}

data class ContextMenuEntrySpec(
    val id: String,
    val label: String,
    val shortcut: String = "",
    val enabled: Boolean = true,
    val sectionBreakBefore: Boolean = false,
)
