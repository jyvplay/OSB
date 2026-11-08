// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import dev.fusion.ooxml.OfficeSheetTextGrid
import dev.fusion.ooxml.OfficeSheetRangeEdit

/** Bounded local authoring, deliberately separate from the blocked untrusted Office importer. */
@Composable
fun SheetScreen(
    model: SheetViewModel,
    exportBusy: Boolean,
    exportStatus: String,
    onExport: (OfficeSheetDraft.Draft) -> Unit,
) = SheetScreenContent(model, exportBusy, exportStatus, null, onExport)

/** Additional values-only CSV output; the old XLSX-only entry point remains unchanged. */
@Composable
fun SheetScreenWithCsv(
    model: SheetViewModel,
    exportBusy: Boolean,
    exportStatus: String,
    onExportCsv: (OfficeSheetDraft.Draft) -> Unit,
    onExport: (OfficeSheetDraft.Draft) -> Unit,
) = SheetScreenContent(model, exportBusy, exportStatus, onExportCsv, onExport)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetScreenContent(
    model: SheetViewModel,
    exportBusy: Boolean,
    exportStatus: String,
    onExportCsv: ((OfficeSheetDraft.Draft) -> Unit)?,
    onExport: (OfficeSheetDraft.Draft) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val draft = state.data.draft
    val ready = !state.loading && !state.busy && !state.recoveryBlocked && state.textGridPreview == null && state.rangePreview == null && state.copyPreview == null
    val selected = state.selected
    val kind = state.inputKind
    val edit = state.input
    var jump by rememberSaveable { mutableStateOf("A1") }
    var kindMenu by rememberSaveable { mutableStateOf(false) }
    var pendingSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmExportRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmCsvRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var uiMessage by rememberSaveable { mutableStateOf("") }
    // Pasted raw text is deliberately not saveable or persisted outside the applied draft.
    var showTextGridInput by remember { mutableStateOf(false) }
    var textGridInput by remember { mutableStateOf("") }
    var interpretDecimals by remember { mutableStateOf(false) }
    var transposeTextGrid by remember { mutableStateOf(false) }
    var skipBlankFields by remember { mutableStateOf(false) }
    var protectExistingCells by remember { mutableStateOf(false) }
    var showRangeInput by remember { mutableStateOf(false) }
    var rangeInput by remember { mutableStateOf("") }
    var rangeOperation by remember { mutableStateOf(OfficeSheetRangeEdit.Operation.CLEAR_CONTENTS) }
    var rangeProtection by remember { mutableStateOf(true) }
    var showCopyInput by remember { mutableStateOf(false) }
    var copySource by remember { mutableStateOf("") }
    var copyProtection by remember { mutableStateOf(true) }
    var copySkipMissing by remember { mutableStateOf(false) }
    LaunchedEffect(state.copyPreview) {
        if (state.copyPreview != null) { showCopyInput = false; copySource = "" }
    }
    LaunchedEffect(state.rangePreview) {
        if (state.rangePreview != null) { showRangeInput = false; rangeInput = "" }
    }
    LaunchedEffect(state.textGridPreview) {
        if (state.textGridPreview != null) { showTextGridInput = false; textGridInput = "" }
    }
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
        // Keep the export action discoverable at compact widths instead of hiding it in an
        // unmarked horizontal toolbar; wrapping also keeps actions reachable at larger fonts.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(jump, { if (it.length <= 8) jump = it }, modifier = Modifier.width(132.dp).testTag("sheet-address"),
                enabled = ready, singleLine = true, label = { Text("Cell") })
            OutlinedButton(onClick = { selectCell(jump) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-select")) { Text("Go") }
            OutlinedButton(onClick = model::undo, enabled = ready && !unapplied && state.undoDepth > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-undo")) { Text("Undo") }
            OutlinedButton(onClick = model::redo, enabled = ready && !unapplied && state.redoDepth > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-redo")) { Text("Redo") }
            OutlinedButton(onClick = { confirmExportRevision = draft.revision }, enabled = ready && !unapplied && !exportBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-export")) { Text("Export XLSX copy…") }
            if (onExportCsv != null) OutlinedButton(onClick = { confirmCsvRevision = draft.revision }, enabled = ready && !unapplied && !exportBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-export-csv")) { Text("Export CSV values…") }
            OutlinedButton(onClick = {
                model.clearTextGridError(); textGridInput = ""; interpretDecimals = false; transposeTextGrid = false
                skipBlankFields = false; protectExistingCells = false; showTextGridInput = true
            },
                enabled = ready && !unapplied && !exportBusy, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-text-grid")) { Text("Paste text grid…") }
            OutlinedButton(onClick = {
                model.clearRangeError(); rangeInput = selected; rangeProtection = true
                rangeOperation = OfficeSheetRangeEdit.Operation.CLEAR_CONTENTS; showRangeInput = true
            }, enabled = ready && !unapplied && !exportBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-range-clear")) { Text("Clear range…") }
            OutlinedButton(onClick = {
                model.clearRangeError(); rangeInput = selected; rangeProtection = true
                rangeOperation = OfficeSheetRangeEdit.Operation.FILL_LITERAL; showRangeInput = true
            }, enabled = ready && !unapplied && !exportBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-range-fill")) { Text("Fill literal…") }
            OutlinedButton(onClick = {
                model.clearRangeError(); rangeInput = selected; rangeProtection = true
                rangeOperation = OfficeSheetRangeEdit.Operation.FILL_FORMULA; showRangeInput = true
            }, enabled = ready && !unapplied && !exportBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-range-formula")) { Text("Fill formula…") }
            OutlinedButton(onClick = {
                model.clearCopyError(); copySource = selected; copyProtection = true; copySkipMissing = false; showCopyInput = true
            }, enabled = ready && !unapplied && !exportBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-copy-range")) { Text("Copy range…") }
        }
        Text("Selected $selected · displayed value: ${state.data.values[selected]?.displayText().orEmpty()}", modifier = Modifier.testTag("sheet-selected-value"))
        // Wrap the cell actions rather than hiding Clear off the right edge. The
        // surrounding vertical editor is now their only scroll ancestor, so an IME
        // can be scrolled past without trapping a kind/action in an inner horizontal row.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                OutlinedButton(onClick = { kindMenu = true }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-kind")) { Text("Type: ${kind.name.lowercase().replaceFirstChar { it.uppercase() }}") }
                DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                    OfficeSheetDraft.Kind.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = { model.updateKind(option); kindMenu = false })
                    }
                }
            }
            Button(onClick = { model.apply(selected, kind, edit) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-apply")) { Text("Apply cell") }
            OutlinedButton(onClick = { confirmClear = true }, enabled = ready && !unapplied && original != null, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-clear")) { Text("Clear cell…") }
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
    if (showTextGridInput && state.textGridPreview == null) AlertDialog(
        onDismissRequest = { if (!state.busy) { showTextGridInput = false; textGridInput = "" } },
        title = { Text("Paste text grid at $selected") },
        text = {
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tabs separate columns; line endings separate rows. Quote whole fields and double quotes inside them. Rows must be rectangular. By default blank fields clear cells; formulas are never imported. Up to 256 fields and 64 KiB.")
                OutlinedTextField(textGridInput, { if (it.length <= OfficeSheetTextGrid.MAX_INPUT_BYTES) textGridInput = it },
                    modifier = Modifier.fillMaxWidth().testTag("sheet-text-grid-input"), enabled = !state.busy,
                    label = { Text("Enter or paste tab-delimited text") }, minLines = 3, maxLines = 7)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = interpretDecimals, enabled = !state.busy, role = Role.Checkbox,
                    onValueChange = { interpretDecimals = it },
                ).testTag("sheet-text-grid-decimals"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = interpretDecimals, onCheckedChange = null, enabled = !state.busy)
                    Text("Interpret ordinary decimals as numbers")
                }
                Text("Off: all nonblank fields are text. On: qualifying unquoted decimals become approximate binary numbers. Leading zeros, quoted values, exponents, fractional trailing zeros and more than 15 significant digits stay text.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = transposeTextGrid, enabled = !state.busy, role = Role.Checkbox,
                    onValueChange = { transposeTextGrid = it },
                ).testTag("sheet-text-grid-transpose"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = transposeTextGrid, onCheckedChange = null, enabled = !state.busy)
                    Text("Swap rows and columns")
                }
                Text("Both the input and the transposed result must fit the grid limits; quoted fields retain their text/number policy.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = skipBlankFields, enabled = !state.busy, role = Role.Checkbox,
                    onValueChange = { skipBlankFields = it },
                ).testTag("sheet-text-grid-skip-blanks"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = skipBlankFields, onCheckedChange = null, enabled = !state.busy)
                    Text("Keep cells where the input is blank")
                }
                Text("Quoted and unquoted empty fields are skipped; spaces are text. All source and destination bounds still apply.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = protectExistingCells, enabled = !state.busy, role = Role.Checkbox,
                    onValueChange = { protectExistingCells = it },
                ).testTag("sheet-text-grid-protect-existing"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = protectExistingCells, onCheckedChange = null, enabled = !state.busy)
                    Text("Refuse changes to existing cells")
                }
                Text("If a populated cell would be replaced or cleared, the whole grid is refused. An identical value and type, or insertion into an empty cell, is allowed.", style = MaterialTheme.typography.bodySmall)
                if (state.textGridError.isNotEmpty()) Text(state.textGridError,
                    Modifier.testTag("sheet-text-grid-error").semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                if (state.busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); model.previewTextGrid(textGridInput,
                if (interpretDecimals) OfficeSheetTextGrid.Mode.DECIMALS else OfficeSheetTextGrid.Mode.TEXT_ONLY,
                transposeTextGrid, skipBlankFields, protectExistingCells) },
                enabled = !state.busy && textGridInput.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-text-grid-preview")) { Text("Review changes") }
        },
        dismissButton = { TextButton(onClick = { showTextGridInput = false; textGridInput = "" }, enabled = !state.busy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-text-grid-input-cancel")) { Text("Cancel") } },
    )
    state.textGridPreview?.let { plan ->
        AlertDialog(onDismissRequest = model::cancelTextGridPreview, title = { Text("Apply text grid?") },
            text = {
                val types = if (plan.mode == OfficeSheetTextGrid.Mode.TEXT_ONLY) "All nonempty fields are text."
                else "${plan.numericFields} numeric, ${plan.textFields} text and ${plan.emptyFields} blank fields in the grid. Numbers are approximate binary values."
                val operation = if (plan.transposed) "Rows and columns swapped. " else ""
                val blankUnit = if (plan.skippedFields == 1) "field" else "fields"
                val policies = (if (plan.skipBlanks) "${plan.skippedFields} blank $blankUnit skipped. " else "") +
                    (if (plan.protectExisting) "Existing-cell protection is on. " else "")
                Text("$operation${plan.rows} rows × ${plan.columns} columns from ${plan.anchor}: ${plan.inserted} new, ${plan.replaced} replaced, ${plan.cleared} cleared, ${plan.unchanged} unchanged. $policies$types This changes the local draft as one undo step; it does not save an external file.",
                    Modifier.testTag("sheet-text-grid-review-text"))
            },
            confirmButton = { TextButton(onClick = model::acceptTextGrid, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-text-grid-apply")) { Text("Apply grid") } },
            dismissButton = { TextButton(onClick = model::cancelTextGridPreview, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-text-grid-cancel")) { Text("Cancel") } })
    }
    if (showRangeInput && state.rangePreview == null) AlertDialog(
        onDismissRequest = { if (!state.busy) { showRangeInput = false; rangeInput = "" } },
        title = { Text(if (rangeOperation == OfficeSheetRangeEdit.Operation.CLEAR_CONTENTS) "Clear cell range?" else "Fill from $selected?") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter one cell or a rectangle such as A1:C5. At most 256 positions within A1:Z100. Reversed corners are normalized.")
                OutlinedTextField(rangeInput, { if (it.length <= 32) rangeInput = it },
                    modifier = Modifier.fillMaxWidth().testTag("sheet-range-input"), enabled = !state.busy,
                    label = { Text("Cell or range") }, singleLine = true)
                if (rangeOperation != OfficeSheetRangeEdit.Operation.CLEAR_CONTENTS) {
                    if (rangeOperation == OfficeSheetRangeEdit.Operation.FILL_LITERAL)
                        Text("Copies the applied nonempty text, number or Boolean in $selected with its type. Formula sources are refused; no relative-reference adjustment or formatting is performed.")
                    else Text("Copies the applied formula in $selected. Relative references move with each destination; dollar-anchored rows or columns stay fixed. If a reference leaves A1:Z100, a cycle results, or a draft limit is exceeded, the whole fill is refused. This is not full Excel compatibility.", Modifier.testTag("sheet-range-formula-disclosure"))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = rangeProtection, enabled = !state.busy, role = Role.Checkbox,
                        onValueChange = { rangeProtection = it },
                    ).testTag("sheet-range-protect"), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rangeProtection, onCheckedChange = null, enabled = !state.busy)
                        Text("Protect existing cells")
                    }
                    Text("On by default: a different populated value refuses the whole fill. Turning it off can replace values or formulas; the next screen reports counts.", style = MaterialTheme.typography.bodySmall)
                } else Text("Clears values and formulas in the rectangle, not the cells outside it. This sheet does not support styles.")
                if (state.rangeError.isNotEmpty()) Text(state.rangeError,
                    Modifier.testTag("sheet-range-error").semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                if (state.busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); model.previewRange(rangeInput, rangeOperation, rangeProtection) },
                enabled = !state.busy && rangeInput.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-range-preview")) { Text("Review changes") }
        },
        dismissButton = { TextButton(onClick = { showRangeInput = false; rangeInput = "" }, enabled = !state.busy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-range-input-cancel")) { Text("Cancel") } },
    )
    state.rangePreview?.let { plan ->
        AlertDialog(onDismissRequest = model::cancelRangePreview, title = { Text("Apply range changes?") },
            text = {
                val operation = when (plan.operation) {
                    OfficeSheetRangeEdit.Operation.CLEAR_CONTENTS -> "Clear contents"
                    OfficeSheetRangeEdit.Operation.FILL_LITERAL -> "Fill with the applied ${plan.original.cells[plan.selected]?.kind?.name} value from ${plan.selected}"
                    OfficeSheetRangeEdit.Operation.FILL_FORMULA -> "Fill adjusted formula from ${plan.selected}"
                }
                val protection = if (plan.operation != OfficeSheetRangeEdit.Operation.CLEAR_CONTENTS)
                    if (plan.protectExisting) " Existing-cell protection is on." else " Existing-cell protection is off." else ""
                val undo = if (plan.changed == 0) "Nothing will change and no undo step will be added."
                    else "This changes the local draft as one undo step; it does not save an external file."
                val formulaWord = if (plan.formulasRemoved == 1) "formula" else "formulas"
                val formulaSummary = if (plan.operation == OfficeSheetRangeEdit.Operation.FILL_FORMULA)
                    "${plan.formulasRemoved} old $formulaWord replaced; ${plan.formulasWritten} formulas written."
                    else "${plan.formulasRemoved} $formulaWord removed."
                Text("$operation: ${plan.range} (${plan.positions} positions). ${plan.inserted} new, ${plan.replaced} replaced, ${plan.cleared} cleared, ${plan.unchanged} unchanged; $formulaSummary$protection $undo",
                    Modifier.testTag("sheet-range-review-text"))
            },
            confirmButton = { TextButton(onClick = model::acceptRange, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-range-apply")) { Text("Apply range") } },
            dismissButton = { TextButton(onClick = model::cancelRangePreview, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-range-cancel")) { Text("Cancel") } })
    }
    if (showCopyInput && state.copyPreview == null) AlertDialog(
        onDismissRequest = { if (!state.busy) { showCopyInput = false; copySource = "" } },
        title = { Text("Copy a range to $selected?") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter one source cell or rectangle in A1:Z100, up to 256 positions. $selected is the top-left destination. This is a local sheet operation, not the system clipboard.")
                OutlinedTextField(copySource, { if (it.length <= 32) copySource = it },
                    modifier = Modifier.fillMaxWidth().testTag("sheet-copy-source"), enabled = !state.busy,
                    label = { Text("Source cell or range") }, singleLine = true)
                Text("Copies applied text, numbers, booleans and supported formulas. Relative references move; dollar-anchored parts stay fixed. Out-of-range references, cycles or limits refuse the whole copy. No styles or Excel parity.")
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = copyProtection, enabled = !state.busy, role = Role.Checkbox, onValueChange = { copyProtection = it },
                ).testTag("sheet-copy-protect"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyProtection, onCheckedChange = null, enabled = !state.busy)
                    Text("Protect existing destination cells")
                }
                Text("On by default: replacing or clearing a different populated cell refuses the whole copy.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = copySkipMissing, enabled = !state.busy, role = Role.Checkbox, onValueChange = { copySkipMissing = it },
                ).testTag("sheet-copy-skip-missing"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copySkipMissing, onCheckedChange = null, enabled = !state.busy)
                    Text("Keep destinations for missing source cells")
                }
                Text("Off: missing source cells clear destinations. A stored empty-text cell is copied as text, not skipped. Overlapping ranges use the original values.", style = MaterialTheme.typography.bodySmall)
                if (state.copyError.isNotEmpty()) Text(state.copyError,
                    Modifier.testTag("sheet-copy-error").semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                if (state.busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); model.previewCopy(copySource, copyProtection, copySkipMissing) },
                enabled = !state.busy && copySource.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-copy-preview")) { Text("Review changes") }
        },
        dismissButton = { TextButton(onClick = { showCopyInput = false; copySource = "" }, enabled = !state.busy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-copy-input-cancel")) { Text("Cancel") } },
    )
    state.copyPreview?.let { plan ->
        AlertDialog(onDismissRequest = model::cancelCopyPreview, title = { Text("Apply range copy?") },
            text = {
                val protect = if (plan.protectExisting) "on" else "off"
                val missing = if (plan.skipMissing) "Missing sources are skipped." else "Missing sources clear destinations."
                val undo = if (plan.changed == 0) "Nothing will change; no undo step is added."
                    else "One local undo step; this does not save an external file."
                Text("${plan.source} → ${plan.destination} (${plan.positions} positions): ${plan.inserted} new, ${plan.replaced} replaced, ${plan.cleared} cleared, ${plan.unchanged} unchanged, ${plan.skipped} skipped. ${plan.formulasRemoved} formulas removed; ${plan.formulasWritten} formulas written. Protection $protect. $missing $undo",
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).testTag("sheet-copy-review-text"))
            },
            confirmButton = { TextButton(onClick = model::acceptCopy, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-copy-apply")) { Text("Apply copy") } },
            dismissButton = { TextButton(onClick = model::cancelCopyPreview, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-copy-cancel")) { Text("Cancel") } })
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
    confirmCsvRevision?.let { revision ->
        AlertDialog(onDismissRequest = { confirmCsvRevision = null }, title = { Text("Export CSV values copy?") },
            text = { Text("UTF-8 CSV from A1 to the last populated row and column. Formulas become calculated values; formulas, types and formatting are lost. Other apps may reinterpret fields. Common formula-like text is refused, but this is not a universal CSV-injection guarantee. Choose a new file; the draft stays unchanged. Provider writes may not be atomic.",
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).testTag("sheet-csv-disclosure")) },
            confirmButton = { TextButton(onClick = {
                confirmCsvRevision = null
                val current = model.state.value
                if (!current.loading && !current.busy && !current.recoveryBlocked && !current.unapplied &&
                    current.data.draft.revision == revision && !exportBusy) onExportCsv?.invoke(current.data.draft)
                else uiMessage = "The draft or export state changed; review it and request export again."
            }, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-csv-confirm")) { Text("Choose destination") } },
            dismissButton = { TextButton(onClick = { confirmCsvRevision = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-csv-cancel")) { Text("Cancel") } })
    }
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
