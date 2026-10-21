// SPDX-License-Identifier: Apache-2.0
package dev.fusion.photos

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PhotosWorkbenchScreen() {
    var albumFilter by remember { mutableStateOf("All") }
    var items by remember {
        mutableStateOf(
            listOf(
                PhotoMetadataCleaner.AlbumItem("img-1", "studio-render-01.jpg", 1920, 1080, "Work", strippedSegments = 2),
                PhotoMetadataCleaner.AlbumItem("img-2", "field-survey-02.jpg", 1600, 1200, "Archive", strippedSegments = 3)
            )
        )
    }
    val albums = listOf("All", "Work", "Archive")

    Column(
        Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Photos & Lossless EXIF Cleaner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Strips JPEG APP1 (EXIF/GPS/XMP), APP2..APP15, COM comments, and trailing stego-append bytes while copying SOS..EOI MCU scan bits losslessly without re-encoding.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            albums.forEach { album ->
                FilterChip(
                    selected = albumFilter == album,
                    onClick = { albumFilter = album },
                    label = { Text(album) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("photos-album-${album.lowercase()}")
                )
            }
        }
        items.filter { albumFilter == "All" || it.album == albumFilter }.forEach { item ->
            Column(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("${item.title} (${item.width}×${item.height})", fontWeight = FontWeight.SemiBold)
                Text(
                    "Album: ${item.album} · Stripped segments: ${item.strippedSegments} · Aspect: ${item.aspectPreset}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Original", "16:9", "1:1").forEach { ratio ->
                        OutlinedButton(
                            onClick = {
                                items = items.map { if (it.id == item.id) it.copy(aspectPreset = ratio) else it }
                            },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("photos-ratio-${item.id}-$ratio")
                        ) { Text(ratio) }
                    }
                }
            }
        }
    }
}
