// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fusion.ooxml.OfficeSheetDraft

/** Bounded local authoring, deliberately separate from the blocked untrusted Office importer. */
@Composable
fun SheetScreen(
    model: SheetViewModel,
    exportBusy: Boolean,
    exportStatus: String,
    onExport: (OfficeSheetDraft.Draft) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val draft = state.data.draft
    val ready = !state.loading && !state.busy && !state.recoveryBlocked
    val selected = state.selected
    val kind = state.inputKind
    val edit = state.input
    var jump by rememberSaveable { mutableStateOf("A1") }
    var kindMenu by rememberSaveable { mutableStateOf(false) }
    var pendingSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmExportRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var uiMessage by rememberSaveable { mutableStateOf("") }
    val original = draft.cells[selected]
    val unapplied = state.unapplied

    LaunchedEffect(selected) { jump = selected }

    val selectCell: (String) -> Unit = { raw ->
        val target = runCatching { OfficeSheetDraft.address(raw) }.getOrNull()
        when {
            target == null -> uiMessage = "Use a cell address from A1 to Z100."
            target == selected -> jump = selected
            unapplied -> pendingSelection = target
            else -> { model.select(target); jump = target; uiMessage = "" }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
        .semantics { paneTitle = "Spreadsheet" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Spreadsheet", style = MaterialTheme.typography.titleLarge)
        Text("Local single-sheet authoring • A1–Z100 • 256 populated cells • 64 formulas. No XLSX import, styles or full Excel compatibility.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(jump, { if (it.length <= 8) jump = it }, modifier = Modifier.width(132.dp).testTag("sheet-address"),
                enabled = ready, singleLine = true, label = { Text("Cell") })
            OutlinedButton(onClick = { selectCell(jump) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-select")) { Text("Go") }
            OutlinedButton(onClick = model::undo, enabled = ready && !unapplied && state.undoDepth > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-undo")) { Text("Undo") }
            OutlinedButton(onClick = model::redo, enabled = ready && !unapplied && state.redoDepth > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("Redo") }
            OutlinedButton(onClick = { confirmExportRevision = draft.revision }, enabled = ready && !unapplied && !exportBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-export")) { Text("Export XLSX copy…") }
        }
        Text("Selected $selected · displayed value: ${state.data.values[selected]?.displayText().orEmpty()}", modifier = Modifier.testTag("sheet-selected-value"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { kindMenu = true }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-kind")) { Text("Type: ${kind.name.lowercase().replaceFirstChar { it.uppercase() }}") }
                DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                    OfficeSheetDraft.Kind.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = { model.updateKind(option); kindMenu = false })
                    }
                }
            }
            Button(onClick = { model.apply(selected, kind, edit) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-apply")) { Text("Apply cell") }
            OutlinedButton(onClick = { confirmClear = true }, enabled = ready && !unapplied && original != null, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear cell…") }
        }
        OutlinedTextField(edit, model::updateInput,
            modifier = Modifier.fillMaxWidth().testTag("sheet-input"), enabled = ready,
            label = { Text("Input for $selected") }, minLines = 1, maxLines = 4)
        Text("Text stays text, even if it starts with =. Number uses a decimal point; Boolean is TRUE/FALSE. Formula starts with =; unsupported names/functions and cycles are refused. Apply before selecting a different cell or exporting. Unapplied input survives hiding this window, but not process death.", style = MaterialTheme.typography.bodySmall)
        if (unapplied) Text("Unapplied cell input", color = MaterialTheme.colorScheme.primary)
        if (state.loading || state.busy) CircularProgressIndicator()
        Text(state.status, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (state.checkpoint.isNotEmpty()) Text(state.checkpoint, style = MaterialTheme.typography.bodySmall)
        if (exportStatus.isNotEmpty()) Text(exportStatus, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (uiMessage.isNotEmpty()) Text(uiMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text("Grid: scroll horizontally for columns and vertically inside the grid for rows.", style = MaterialTheme.typography.bodySmall)
        val gridWidth = (48 + OfficeSheetDraft.MAX_COLUMNS * 104).dp
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(Modifier.width(gridWidth)) {
                Box(Modifier.width(48.dp).height(48.dp), contentAlignment = Alignment.Center) { Text("#") }
                for (column in 0 until OfficeSheetDraft.MAX_COLUMNS) {
                    Box(Modifier.width(104.dp).height(48.dp).border(0.5.dp, MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) {
                        Text(('A' + column).toString())
                    }
                }
            }
            LazyColumn(Modifier.width(gridWidth).height(288.dp).testTag("sheet-grid")) {
                items((1..OfficeSheetDraft.MAX_ROWS).toList()) { row ->
                    Row {
                        Box(Modifier.width(48.dp).height(48.dp).border(0.5.dp, MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) { Text(row.toString()) }
                        for (column in 0 until OfficeSheetDraft.MAX_COLUMNS) {
                            val ref = "${'A' + column}$row"
                            val value = state.data.values[ref]?.displayText().orEmpty()
                            Box(Modifier.width(104.dp).height(48.dp).border(if (ref == selected) 2.dp else 0.5.dp,
                                if (ref == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                .clickable(enabled = ready, role = Role.Button) { selectCell(ref) }
                                .semantics { contentDescription = "Cell $ref: $value" }.testTag("sheet-cell-$ref").padding(4.dp),
                                contentAlignment = Alignment.CenterStart) {
                                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    pendingSelection?.let { target ->
        AlertDialog(onDismissRequest = { pendingSelection = null }, title = { Text("Discard unapplied cell input?") },
            text = { Text("Only the already applied sheet is retained. Selecting $target discards the input currently in the editor.") },
            confirmButton = { TextButton(onClick = { pendingSelection = null; model.select(target, discardInput = true); jump = target }) { Text("Discard and select") } },
            dismissButton = { TextButton(onClick = { pendingSelection = null }) { Text("Keep editing") } })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear $selected?") },
        text = { Text("Removes this cell from the local draft; Undo is available until the app process ends.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; model.clear(selected) }) { Text("Clear cell") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
    confirmExportRevision?.let { revision ->
        AlertDialog(onDismissRequest = { confirmExportRevision = null }, title = { Text("Export XLSX copy?") },
            text = { Text("Exports the applied cells and calculated values to a new single-sheet XLSX. No imported workbook, styles, charts, macros or external links are preserved. Formula support and numerical behavior are limited; verify results independently. The local draft remains unchanged. A provider may not offer atomic creation or durability.", Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = {
                confirmExportRevision = null
                val current = model.state.value
                if (!current.loading && !current.busy && !current.recoveryBlocked && current.data.draft.revision == revision) onExport(current.data.draft)
                else uiMessage = "The draft changed; review it and request export again."
            }) { Text("Choose destination") } },
            dismissButton = { TextButton(onClick = { confirmExportRevision = null }) { Text("Cancel") } })
    }
}
