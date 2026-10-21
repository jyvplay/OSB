// SPDX-License-Identifier: Apache-2.0
package dev.fusion.office

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OfficeWorkbenchScreen(controller: OfficeSessionController = remember { OfficeSessionController() }) {
    var snapshot by remember { mutableStateOf(controller.state) }
    var cellRef by remember { mutableStateOf("B3") }
    var cellExpr by remember { mutableStateOf("=B1*2") }
    var slideTitle by remember { mutableStateOf("") }
    var slideBullets by remember { mutableStateOf("") }

    fun sync(block: () -> Unit) {
        runCatching { block() }.onFailure {
            controller.updateDocument(snapshot.docTitle, snapshot.docBody)
        }
        snapshot = controller.state
    }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Office Workbench · Clean-Room OOXML", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Apache-2.0 JVM engine (.docx / .xlsx / .pptx) with side-effect-free formula evaluation. Macros (vbaProject.bin) and external entities are strictly refused; print pagination remains separate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OfficeTab.entries.forEach { tab ->
                FilterChip(
                    selected = snapshot.activeTab == tab,
                    onClick = { controller.selectTab(tab); snapshot = controller.state },
                    label = { Text(tab.label) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("office-tab-${tab.name.lowercase()}")
                )
            }
            OutlinedButton(
                onClick = { controller.undo(); snapshot = controller.state },
                enabled = controller.canUndo,
                modifier = Modifier.heightIn(min = 48.dp).testTag("office-undo")
            ) { Text("Undo") }
            OutlinedButton(
                onClick = { controller.redo(); snapshot = controller.state },
                enabled = controller.canRedo,
                modifier = Modifier.heightIn(min = 48.dp).testTag("office-redo")
            ) { Text("Redo") }
        }

        when (snapshot.activeTab) {
            OfficeTab.CALC_GRID -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = cellRef,
                        onValueChange = { if (it.length <= 8) cellRef = it },
                        label = { Text("Cell") },
                        singleLine = true,
                        modifier = Modifier.width(104.dp).testTag("office-cell-ref")
                    )
                    OutlinedTextField(
                        value = cellExpr,
                        onValueChange = { if (it.length <= 256) cellExpr = it },
                        label = { Text("Value or =Formula") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("office-cell-expr")
                    )
                    Button(
                        onClick = {
                            runCatching { controller.updateCellInput(cellRef, cellExpr) }
                            snapshot = controller.state
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("office-cell-apply")
                    ) { Text("Apply") }
                }
                val evaluated = snapshot.evaluatedCells()
                Column(
                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    snapshot.sheetCells.entries.sortedBy { it.key }.forEach { (ref, cell) ->
                        val computed = OfficeSessionController.formatCellValue(evaluated[ref] ?: cell.raw)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ref, fontWeight = FontWeight.Bold)
                            Text(cell.formula?.let { "=$it" } ?: computed, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(computed, Modifier.testTag("office-val-$ref"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            OfficeTab.WRITER -> {
                var titleDraft by remember(snapshot.revision) { mutableStateOf(snapshot.docTitle) }
                var bodyDraft by remember(snapshot.revision) { mutableStateOf(snapshot.docBody) }
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { if (it.length <= 256) titleDraft = it },
                    label = { Text("Heading") },
                    modifier = Modifier.fillMaxWidth().testTag("office-doc-title")
                )
                OutlinedTextField(
                    value = bodyDraft,
                    onValueChange = { if (it.length <= 65536) bodyDraft = it },
                    label = { Text("Document body") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("office-doc-body")
                )
                Button(
                    onClick = { sync { controller.updateDocument(titleDraft, bodyDraft) } },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("office-doc-save")
                ) { Text("Commit draft revision") }
            }
            OfficeTab.SLIDES -> {
                snapshot.slides.forEach { slide ->
                    Column(
                        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Slide ${slide.number}: ${slide.title}", fontWeight = FontWeight.SemiBold)
                        slide.bullets.forEach { bullet -> Text("• $bullet", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                OutlinedTextField(slideTitle, { if (it.length <= 120) slideTitle = it }, label = { Text("New slide title") }, modifier = Modifier.fillMaxWidth().testTag("office-slide-title"))
                OutlinedTextField(slideBullets, { if (it.length <= 400) slideBullets = it }, label = { Text("Points (; separated)") }, modifier = Modifier.fillMaxWidth().testTag("office-slide-bullets"))
                Button(
                    onClick = {
                        if (slideTitle.isNotBlank()) {
                            controller.addSlide(slideTitle, slideBullets)
                            slideTitle = ""; slideBullets = ""
                            snapshot = controller.state
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("office-slide-add")
                ) { Text("Add slide") }
            }
        }
        Text(snapshot.status, Modifier.testTag("office-status"), style = MaterialTheme.typography.bodySmall)
    }
}
