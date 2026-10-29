// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

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
 * **Media Hub `List` / `Grid` / `Columns` Multi-View Collection Panel** rendering our original
 * pixel-grid crisp-hinted `List` (`Spine-Ledger`), `Grid` (`Aperture-Mosaic`), and `Columns`
 * (`Cascading Miller-Triptych` `Artist -> Album -> Track`) view modes alongside Recall silence-split
 * track cues!
 */
@Composable
fun MediaLibraryMultiViewPanel(
    items: List<MediaCollectionViewModeGrouper.MediaItemRecord>,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(FusionViewModeVectorIcons.ViewModeKind.COLUMNS) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    val columnsProjection = remember(items, selectedArtist, selectedAlbum) {
        MediaCollectionViewModeGrouper.projectColumns(items, selectedArtist, selectedAlbum)
    }

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag("media-library-multiview-panel"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recall & Local Media Collection", style = MaterialTheme.typography.titleSmall)
                CrispHintedViewModeToolbar(
                    activeMode = mode,
                    onModeChanged = { mode = it },
                    surfaceLabel = "media",
                )
            }
            when (mode) {
                FusionViewModeVectorIcons.ViewModeKind.LIST -> {
                    items.forEach { rec ->
                        Text("${rec.title} — ${rec.artistOrOrigin} (${rec.durationMillis / 1000}s)", style = MaterialTheme.typography.bodySmall)
                    }
                }
                FusionViewModeVectorIcons.ViewModeKind.GRID -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.take(4).forEach { rec ->
                            Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.small) {
                                Text(rec.title, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                FusionViewModeVectorIcons.ViewModeKind.COLUMNS -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Origins / Artists", style = MaterialTheme.typography.labelSmall)
                            columnsProjection.artists.forEach { a ->
                                Text(if (a == columnsProjection.selectedArtist) "▸ $a" else a, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Vault Folders / Albums", style = MaterialTheme.typography.labelSmall)
                            columnsProjection.albums.forEach { al ->
                                Text(if (al == columnsProjection.selectedAlbum) "▸ $al" else al, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Tracks (${columnsProjection.tracks.size})", style = MaterialTheme.typography.labelSmall)
                            columnsProjection.tracks.forEach { tr ->
                                Text(tr.title, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
