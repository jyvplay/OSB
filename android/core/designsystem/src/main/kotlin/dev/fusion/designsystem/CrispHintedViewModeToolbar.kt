// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * **Pixel-Grid Crisp-Hinted Universal `List` / `Grid` / `Columns` Logo Toolbar** powered by
 * `ViewModePixelGridCrispHinting` + `FusionViewModeVectorIcons` so every collection screen across
 * `Files`, `Media`, `Vault`, `Notes`, `Recall`, and `Launcher` renders crisp, half-pixel-aligned
 * original vector logos at `16 px`..`48 px`!
 */
@Composable
fun CrispHintedViewModeToolbar(
    activeMode: FusionViewModeVectorIcons.ViewModeKind,
    onModeChanged: (FusionViewModeVectorIcons.ViewModeKind) -> Unit,
    surfaceLabel: String,
    iconSizeDp: Dp = 20.dp,
    compactIconOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("crisp-view-mode-toolbar-$surfaceLabel"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FusionViewModeVectorIcons.ViewModeKind.entries.forEach { mode ->
            val selected = mode == activeMode
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            FilterChip(
                selected = selected,
                onClick = { onModeChanged(mode) },
                leadingIcon = {
                    CrispHintedViewModeCanvas(kind = mode, tint = tint, sizeDp = iconSizeDp)
                },
                label = {
                    Text(if (compactIconOnly) mode.buttonLabel.take(1) else mode.buttonLabel)
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = "$surfaceLabel ${mode.buttonLabel} mode (${mode.designCodename})"
                    }
                    .testTag("crisp-mode-$surfaceLabel-${mode.name.lowercase()}"),
            )
        }
    }
}

@Composable
fun CrispHintedViewModeCanvas(
    kind: FusionViewModeVectorIcons.ViewModeKind,
    tint: Color,
    sizeDp: Dp = 20.dp,
) {
    val density = LocalDensity.current
    val targetPx = remember(sizeDp, density) {
        with(density) { sizeDp.toPx().roundToInt().coerceIn(16, 96) }
    }
    val hinted = remember(kind, targetPx) {
        ViewModePixelGridCrispHinting.hintIconForCanvasPx(kind, targetPx)
    }
    Canvas(Modifier.size(sizeDp)) {
        val ratioX = size.width / targetPx.toFloat()
        val ratioY = size.height / targetPx.toFloat()
        for (seg in hinted.hintedSegments) {
            drawLine(
                color = tint,
                start = Offset(seg.x1Px.toFloat() * ratioX, seg.y1Px.toFloat() * ratioY),
                end = Offset(seg.x2Px.toFloat() * ratioX, seg.y2Px.toFloat() * ratioY),
                strokeWidth = seg.integerStrokeWidthPx.toFloat() * minOf(ratioX, ratioY),
                cap = StrokeCap.Round,
            )
        }
    }
}
