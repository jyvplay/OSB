// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * **Bottom-Right Stacked Front-to-Back Translucent Undo & Action Notification Host**.
 *
 * Renders up to `3` cards in perspective Z-depth (`alpha = 0.88f, 0.68f, 0.48f`;
 * `scale = 1.00f, 0.95f, 0.90f`; `offsetY = 0.dp, -10.dp, -20.dp`) anchored strictly in the
 * **bottom-right corner** with width capped at `min(340.dp, 0.32 * viewportWidthDp)` so
 * notifications never monopolize the right side of the page!
 */
@Composable
fun StackedUndoNotificationHost(
    notificationsNewestFirst: List<StackedToastUiModel>,
    viewportWidthDp: Double,
    onTriggerUndo: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notificationsNewestFirst.isEmpty()) return
    val visible = notificationsNewestFirst.take(3)
    val hiddenOlder = (notificationsNewestFirst.size - visible.size).coerceAtLeast(0)
    val maxCardW = if (viewportWidthDp >= 640.0) minOf(340.0, viewportWidthDp * 0.32) else minOf(340.0, viewportWidthDp - 32.0)

    val alphas = floatArrayOf(0.88f, 0.68f, 0.48f)
    val scales = floatArrayOf(1.00f, 0.95f, 0.90f)

    Box(
        modifier = modifier
            .padding(end = 16.dp, bottom = 16.dp)
            .width(maxCardW.dp)
            .widthIn(max = 340.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("bottom-right-stacked-undo-host"),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // Draw back-to-front so depth 0 sits crisply in front!
        for (depth in visible.indices.reversed()) {
            val item = visible[depth]
            val layerAlpha = alphas[depth]
            val layerScale = scales[depth]
            val peekOffsetY = (-10 * depth).dp

            Surface(
                color = Color(0xFF14211E).copy(alpha = layerAlpha),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = (6 - depth * 2).dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = peekOffsetY)
                    .zIndex((10 - depth).toFloat())
                    .graphicsLayer(
                        scaleX = layerScale,
                        scaleY = layerScale,
                        alpha = layerAlpha,
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF5CE1C6).copy(alpha = layerAlpha * 0.55f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .testTag("stacked-undo-card-depth-$depth"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val badgePrefix = if (depth == visible.lastIndex && hiddenOlder > 0) "(+$hiddenOlder) " else ""
                    Text(
                        text = badgePrefix + item.message,
                        color = Color(0xFFECFAF6),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (depth == 0 && item.undoLabel != null) {
                        TextButton(
                            onClick = { onTriggerUndo(item.id) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("stacked-undo-action-btn"),
                        ) {
                            Text(item.undoLabel, color = Color(0xFF5CE1C6))
                        }
                    } else if (depth == 0) {
                        TextButton(
                            onClick = { onDismiss(item.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("×", color = Color(0xFFA8C5BE))
                        }
                    }
                }
            }
        }
    }
}

data class StackedToastUiModel(
    val id: String,
    val message: String,
    val undoLabel: String? = "Undo",
)
