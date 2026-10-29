// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fusion.designsystem.FusionViewModeVectorIcons
import dev.fusion.files.FileEntry

/**
 * Multi-mode directory collection renderer for `FilesScreen` supporting **`LIST`**, **`GRID`**,
 * and **`COLUMNS` (Miller Columns)** with compact right-click / long-press context menu hooks.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FilesViewCollectionPane(
    mode: FusionViewModeVectorIcons.ViewModeKind,
    breadcrumbSegments: List<String>,
    entries: List<FileEntry>,
    selectedEntry: FileEntry?,
    onPrimaryClick: (FileEntry) -> Unit,
    onContextClick: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        FusionViewModeVectorIcons.ViewModeKind.LIST -> {
            LazyColumn(modifier.testTag("files-collection-list")) {
                items(entries, key = { it.uri.toString() }) { e ->
                    Text(
                        text = (if (e.isDirectory) "[Folder] " else "[File] ") + e.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selectedEntry?.uri == e.uri) Color(0xFF28453C) else Color.Transparent)
                            .combinedClickable(
                                onClick = { onPrimaryClick(e) },
                                onLongClick = { onContextClick(e) },
                            )
                            .padding(9.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        FusionViewModeVectorIcons.ViewModeKind.GRID -> {
            LazyColumn(modifier.testTag("files-collection-grid")) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entries.forEach { e ->
                            val active = selectedEntry?.uri == e.uri
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                tonalElevation = if (active) 4.dp else 1.dp,
                                modifier = Modifier
                                    .width(156.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (active) Color(0xFF5CE1C6) else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .combinedClickable(
                                        onClick = { onPrimaryClick(e) },
                                        onLongClick = { onContextClick(e) },
                                    ),
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (e.isDirectory) "FOLDER" else "FILE", style = MaterialTheme.typography.labelSmall)
                                    Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text("${e.size} B", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        FusionViewModeVectorIcons.ViewModeKind.COLUMNS -> {
            Row(
                modifier = modifier.fillMaxWidth().testTag("files-collection-columns"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Column 1: Ancestor trail
                Column(
                    Modifier
                        .width(150.dp)
                        .fillMaxHeight()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Hierarchy", style = MaterialTheme.typography.labelMedium)
                    breadcrumbSegments.ifEmpty { listOf("Root") }.forEach { seg ->
                        Text("▸ $seg", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Column 2: Active directory items
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(6.dp),
                ) {
                    items(entries, key = { it.uri.toString() }) { e ->
                        Text(
                            text = (if (e.isDirectory) "▸ " else "• ") + e.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selectedEntry?.uri == e.uri) Color(0xFF28453C) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { onPrimaryClick(e) },
                                    onLongClick = { onContextClick(e) },
                                )
                                .padding(7.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Column 3: Leaf / Selection Inspector
                Box(
                    Modifier
                        .width(176.dp)
                        .fillMaxHeight()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    if (selectedEntry == null) {
                        Text("Select an item to inspect in Column 3", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(selectedEntry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (selectedEntry.isDirectory) "Directory" else "Document (${selectedEntry.size} bytes)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
