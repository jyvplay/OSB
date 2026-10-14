package dev.fusion.filesui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import dev.fusion.files.FileEntry
import dev.fusion.files.SafRepository
import dev.fusion.files.TrashEntry
import dev.fusion.files.TrashRepository
import kotlinx.coroutines.launch

/** Read-only SAF browser. Legacy mutation APIs also refuse in the backend until S6.
 * Trash receipts are historical metadata, not verified reversible-delete support.
 */
@Composable
fun FilesScreen(
    repo: SafRepository,
    trash: TrashRepository,
    treeUri: Uri?,
    onChooseFolder: () -> Unit,
    onOpenText: (FileEntry) -> Unit,
    onMessage: (String) -> Unit,
    mutationsAllowed: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var stack by remember(treeUri) { mutableStateOf(emptyList<DocumentFile>()) }
    var entries by remember(treeUri) { mutableStateOf(emptyList<FileEntry>()) }
    var refresh by remember(treeUri) { mutableIntStateOf(0) }
    var selected by remember(treeUri) { mutableStateOf<FileEntry?>(null) }
    var folderName by remember { mutableStateOf("") }
    var showTrash by remember { mutableStateOf(false) }
    var trashEntries by remember { mutableStateOf<List<TrashEntry>>(emptyList()) }
    var trashRefresh by remember { mutableIntStateOf(0) }
    val dir = stack.lastOrNull()

    LaunchedEffect(treeUri) { stack = treeUri?.let(repo::tree)?.let(::listOf) ?: emptyList() }
    LaunchedEffect(dir?.uri, refresh) {
        // A failed new-folder query must not leave old-folder entries under its breadcrumb.
        entries = emptyList()
        selected = null
        if (dir != null) {
            repo.list(dir).fold({ entries = it }, { onMessage("Listing failed: ${it.message}") })
        } else entries = emptyList()
    }
    LaunchedEffect(trashRefresh, showTrash) {
        if (showTrash) {
            trashEntries = emptyList()
            try { trashEntries = trash.list() }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onMessage("Legacy trash receipts could not be read; existing bytes retained (${e.javaClass.simpleName}).")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        if (!mutationsAllowed) Text("Pilot read-only Files: browse/open only. Copy, creation and trash need the S6 fault matrix.")
        if (treeUri == null) {
            Text("No folder access. Only folders you choose are visible.")
            Button(onClick = onChooseFolder) { Text("Choose a folder") }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dir?.name ?: "Files", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlinedButton(
                onClick = { if (stack.size > 1) stack = stack.dropLast(1) },
                enabled = stack.size > 1
            ) { Text("Up") }
            OutlinedButton(onClick = onChooseFolder) { Text("Folder") }
            OutlinedButton(enabled = mutationsAllowed, onClick = { if (mutationsAllowed) { showTrash = !showTrash; trashRefresh++ } }) {
                Text(if (showTrash) "Files" else "Trash")
            }
        }

        if (showTrash && mutationsAllowed) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        trash.purgeAll().fold(
                            { onMessage("Permanently removed $it item(s) from trash."); trashRefresh++ },
                            { onMessage("Empty trash failed: ${it.message}") }
                        )
                    }
                }) { Text("Empty trash") }
                OutlinedButton(onClick = { trashRefresh++ }) { Text("Refresh") }
            }
            Text(
                "Restore writes into the folder currently open on the Files tab.",
                style = MaterialTheme.typography.labelSmall
            )
            LazyColumn(Modifier.weight(1f)) {
                items(trashEntries, key = { it.id }) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Legacy receipt: ${e.size} bytes; source removal recorded=${e.sourceRemoved}. Not reverified.",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                enabled = dir != null,
                                onClick = {
                                    val target = dir ?: return@OutlinedButton
                                    scope.launch {
                                        trash.restore(e.id, target).fold(
                                            { name ->
                                                onMessage("Restored as \"$name\".")
                                                trashRefresh++; refresh++
                                            },
                                            { onMessage("Restore failed: ${it.message}") }
                                        )
                                    }
                                }
                            ) { Text("Restore here") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    trash.purge(e.id).fold(
                                        { onMessage("Removed permanently."); trashRefresh++ },
                                        { onMessage("Purge failed: ${it.message}") }
                                    )
                                }
                            }) { Text("Delete forever") }
                        }
                    }
                }
            }
            if (trashEntries.isEmpty()) Text("Trash is empty.")
            return@Column
        }

        if (mutationsAllowed) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                folderName, { folderName = it }, Modifier.weight(1f),
                label = { Text("New folder") }, singleLine = true
            )
            Button(onClick = {
                val target = dir ?: return@Button
                scope.launch {
                    repo.createFolder(target, folderName).fold(
                        { folderName = ""; refresh++ },
                        { onMessage(it.message ?: "Failed") }
                    )
                }
            }) { Text("Create") }
        }

        if (mutationsAllowed && selected != null && selected?.isDirectory == false) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    val e = selected ?: return@OutlinedButton
                    val d = dir ?: return@OutlinedButton
                    scope.launch {
                        val f = repo.child(d, e.uri)
                        if (f == null) onMessage("File no longer exists") else {
                            repo.copyTo(f, d).fold(
                                { onMessage("Copy created: ${it.name}"); refresh++ },
                                { onMessage("Copy failed: ${it.message}") }
                            )
                        }
                    }
                }) { Text("Copy") }

                OutlinedButton(onClick = {
                    val e = selected ?: return@OutlinedButton
                    val d = dir ?: return@OutlinedButton
                    scope.launch {
                        val f = repo.child(d, e.uri)
                        if (f == null) onMessage("File no longer exists") else {
                            trash.moveToTrash(f).fold(
                                { entry ->
                                    onMessage(
                                        if (entry.sourceRemoved)
                                            "Moved \"${entry.name}\" to trash. Restore from the Trash tab."
                                        else
                                            "Copied to trash, but the provider refused to delete the original."
                                    )
                                    selected = null; refresh++; trashRefresh++
                                },
                                { onMessage("Delete failed, original untouched: ${it.message}") }
                            )
                        }
                    }
                }) { Text("Delete to trash") }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(entries, key = { it.uri.toString() }) { e ->
                Text(
                    (if (e.isDirectory) "[Folder] " else "[File] ") + e.name,
                    Modifier.fillMaxWidth()
                        .background(if (selected?.uri == e.uri) Color(0xFF28453C) else Color.Transparent)
                        .clickable {
                            selected = e
                            if (e.isDirectory) {
                                val d = dir ?: return@clickable
                                scope.launch {
                                    val child = repo.child(d, e.uri)
                                    if (child != null) { stack = stack + child; selected = null }
                                }
                            } else if (e.name.endsWith(".txt", true) || e.name.endsWith(".md", true)) {
                                onOpenText(e)
                            }
                        }
                        .padding(9.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            if (mutationsAllowed) "Experimental file operations; cross-folder move is not offered." else "Original documents cannot be modified from this Files panel.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
