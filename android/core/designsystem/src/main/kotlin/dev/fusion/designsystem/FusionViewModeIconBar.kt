// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Universal segmented **View-Mode Selector Bar** rendering Fusion's 100% original from-scratch
 * mathematical vector glyphs (`FusionViewModeVectorIcons`: **Spine-Ledger `List`**, **Aperture-Mosaic
 * `Grid`**, and **Cascading Miller-Triptych `Columns`**) across `Files`, `Recall`, and `Launcher`.
 */
@Composable
fun FusionViewModeIconBar(
    selectedMode: FusionViewModeVectorIcons.ViewModeKind,
    onSelectMode: (FusionViewModeVectorIcons.ViewModeKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("fusion-view-mode-bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FusionViewModeVectorIcons.ViewModeKind.entries.forEach { kind ->
            val isSelected = selectedMode == kind
            val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            FilterChip(
                selected = isSelected,
                onClick = { onSelectMode(kind) },
                leadingIcon = {
                    FusionBespokeViewModeGlyph(kind = kind, tint = tint, sizeDp = 18.dp)
                },
                label = { Text(kind.buttonLabel) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "${kind.buttonLabel} view (${kind.designCodename})" }
                    .testTag("view-mode-btn-${kind.name.lowercase()}"),
            )
        }
    }
}

@Composable
fun FusionBespokeViewModeGlyph(
    kind: FusionViewModeVectorIcons.ViewModeKind,
    tint: Color,
    sizeDp: Dp = 20.dp,
) {
    val primitives = remember(kind) { FusionViewModeVectorIcons.primitivesFor(kind) }
    Canvas(Modifier.size(sizeDp)) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val strokeScale = minOf(sx, sy)
        for (prim in primitives) {
            when (prim) {
                is FusionViewModeVectorIcons.VectorPrimitive.Segment -> {
                    drawLine(
                        color = tint,
                        start = Offset(prim.x1.toFloat() * sx, prim.y1.toFloat() * sy),
                        end = Offset(prim.x2.toFloat() * sx, prim.y2.toFloat() * sy),
                        strokeWidth = prim.strokeWidth.toFloat() * strokeScale,
                        cap = StrokeCap.Round,
                    )
                }
                is FusionViewModeVectorIcons.VectorPrimitive.ClosedPolygon -> {
                    val path = Path().apply {
                        prim.points.forEachIndexed { idx, (px, py) ->
                            val ox = px.toFloat() * sx
                            val oy = py.toFloat() * sy
                            if (idx == 0) moveTo(ox, oy) else lineTo(ox, oy)
                        }
                        close()
                    }
                    if (prim.filled) {
                        drawPath(path = path, color = tint, style = Fill)
                    } else {
                        drawPath(
                            path = path,
                            color = tint,
                            style = Stroke(
                                width = prim.strokeWidth.toFloat() * strokeScale,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }
    }
}
