// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * **Interactive Bottom-Right Translucent Z-Stack <-> Fan-Out Undo & Action Notification Overlay**.
 *
 * Resting mode keeps up to 3 notifications tightly Z-stacked (`< 9%` of desktop height,
 * `alpha = 0.88f / 0.68f / 0.48f`, `<= 332.dp` width) in the bottom-right corner so they never
 * hog the right side of the page; tapping `Fan (N)` smoothly separates the top 3 cards with live
 * TTL countdown indicators and selective `Undo`!
 */
@Composable
fun StackedUndoFanOverlayBar(
    itemsNewestFirst: List<InteractiveUndoCardModel>,
    viewportWidthDp: Double,
    reducedTransparency: Boolean = false,
    onUndoItem: (String) -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (itemsNewestFirst.isEmpty()) return
    var fanned by remember { mutableStateOf(false) }
    val visible = itemsNewestFirst.take(3)
    val cardWidth = if (viewportWidthDp >= 640.0) minOf(332.0, viewportWidthDp * 0.30) else minOf(332.0, viewportWidthDp - 32.0)
    val baseAlphas = if (reducedTransparency) floatArrayOf(0.98f, 0.94f, 0.90f) else floatArrayOf(0.88f, 0.68f, 0.48f)
    val baseScales = floatArrayOf(1.00f, 0.95f, 0.90f)

    Box(
        modifier = modifier
            .padding(end = 16.dp, bottom = 16.dp)
            .width(cardWidth.dp)
            .widthIn(max = 332.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("stacked-undo-fan-overlay"),
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (!fanned) {
            for (depth in visible.indices.reversed()) {
                val item = visible[depth]
                val alpha = baseAlphas[depth]
                val scale = baseScales[depth]
                UndoGlassCardSurface(
                    item = item,
                    alpha = alpha,
                    fannedMode = false,
                    badgeLabel = if (depth == 0 && itemsNewestFirst.size > 1) "Fan (${itemsNewestFirst.size})" else null,
                    onToggleFan = { fanned = true },
                    onUndo = { onUndoItem(item.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-10 * depth).dp)
                        .zIndex((10 - depth).toFloat())
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                        .testTag("compact-zstack-card-$depth"),
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
                modifier = Modifier.fillMaxWidth().testTag("expanded-fan-column"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { fanned = false }) { Text("Collapse Stack") }
                    TextButton(onClick = onDismissAll) { Text("Clear") }
                }
                visible.forEachIndexed { idx, item ->
                    UndoGlassCardSurface(
                        item = item,
                        alpha = if (reducedTransparency) 0.98f else 0.92f,
                        fannedMode = true,
                        badgeLabel = null,
                        onToggleFan = { fanned = false },
                        onUndo = { onUndoItem(item.id) },
                        modifier = Modifier.fillMaxWidth().testTag("fanned-undo-card-$idx"),
                    )
                }
            }
        }
    }
}

@Composable
private fun UndoGlassCardSurface(
    item: InteractiveUndoCardModel,
    alpha: Float,
    fannedMode: Boolean,
    badgeLabel: String?,
    onToggleFan: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xFF121C22).copy(alpha = alpha),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(
            width = 1.dp,
            color = Color(0xFF5CE1C6).copy(alpha = alpha * 0.55f),
            shape = RoundedCornerShape(12.dp),
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.summary,
                    color = Color(0xFFF0F6FC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (badgeLabel != null) {
                    TextButton(onClick = onToggleFan) { Text(badgeLabel) }
                }
                if (item.canUndo) {
                    TextButton(onClick = onUndo) { Text("Undo", color = Color(0xFF5CE1C6)) }
                }
            }
            if (fannedMode || item.ttlProgress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { item.ttlProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

data class InteractiveUndoCardModel(
    val id: String,
    val summary: String,
    val ttlProgress: Float = 0.75f,
    val canUndo: Boolean = true,
)
