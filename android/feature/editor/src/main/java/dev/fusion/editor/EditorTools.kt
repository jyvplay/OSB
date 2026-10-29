package dev.fusion.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fusion.documents.LiteralQuery
import dev.fusion.documents.LiteralSearch
import dev.fusion.documents.TextBytes

/** Native editor tools. Literal operations only; no catastrophic-backtracking regex engine. */
@Composable
internal fun EditorTools(model: EditorViewModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(true) }
    var line by remember { mutableStateOf("1") }
    var column by remember { mutableStateOf("1") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val ready=!state.loading && !state.busy && !state.toolsBusy && !state.recoveryBlocked && state.replacement==null
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Find, replace and navigate") },
        text={ Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Literal text only. No regex, escapes, normalization or expanding Unicode case folds. Paste real newlines if needed. Matches do not overlap; navigation wraps.")
            OutlinedTextField(query,{ if(it.length<=LiteralSearch.MAX_QUERY_UNITS) { query=it; inputError=null } else inputError="Search limit: 1024 UTF-16 units" },
                enabled=ready,label={ Text("Find literal text") },modifier=Modifier.fillMaxWidth().testTag("editor-find-query"))
            Row {
                Checkbox(matchCase,onCheckedChange={ matchCase=it },enabled=ready,modifier=Modifier.semantics { contentDescription="Match case. Off uses simple Unicode mapping." })
                Text("Match case (off: simple Unicode mapping)")
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(enabled=ready && query.isNotEmpty(),onClick={ model.find(LiteralQuery(query,matchCase),backwards=true) }) { Text("Find previous") }
                OutlinedButton(enabled=ready && query.isNotEmpty(),onClick={ model.find(LiteralQuery(query,matchCase)) },modifier=Modifier.testTag("editor-find-next")) { Text("Find next") }
            }
            OutlinedTextField(replacement,{ if(it.length<=TextBytes.MAX_BYTES) { replacement=it; inputError=null } else inputError="Replacement exceeds editor limit" },
                enabled=ready,label={ Text("Replace with (literal; empty deletes)") },modifier=Modifier.fillMaxWidth().testTag("editor-replacement"))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(enabled=ready && query.isNotEmpty(),onClick={ model.planReplace(LiteralQuery(query,matchCase),replacement,true) }) { Text("Review replace selection") }
                OutlinedButton(enabled=ready && query.isNotEmpty(),onClick={ model.planReplace(LiteralQuery(query,matchCase),replacement,false) },modifier=Modifier.testTag("editor-replace-all-review")) { Text("Review replace all") }
            }
            Text("At most 10,000 matches. Replace is one undoable draft edit and requires confirmation. A changed revision or different tab invalidates the plan.")
            OutlinedTextField(line,{ if(it.length<=9 && it.all { c -> c in '0'..'9' }) line=it },enabled=ready,singleLine=true,label={ Text("Line (1-based)") },modifier=Modifier.fillMaxWidth())
            OutlinedTextField(column,{ if(it.length<=9 && it.all { c -> c in '0'..'9' }) column=it },enabled=ready,singleLine=true,label={ Text("Code-point column (1-based)") },modifier=Modifier.fillMaxWidth())
            OutlinedButton(enabled=ready && (line.toIntOrNull() ?: 0)>0 && (column.toIntOrNull() ?: 0)>0,
                onClick={ model.goTo(line.toInt(),column.toInt()) }) { Text("Go to position") }
            Text("CRLF is one line break; lone CR/LF also break lines. Columns count Unicode code points, not grapheme clusters or visual/tab columns. Close this panel to return focus to the selected text.")
            OutlinedButton(enabled=ready,onClick=model::compareSource,modifier=Modifier.testTag("editor-compare-source")) { Text("Compare draft with current source") }
            Text("Diff: exact line endings; at most2048 lines/side and1,000,000 cells. No merge or file writes. Preview shows at most200 entries/1024 characters each; hidden preview text is not removed from the draft.")
            state.comparison?.let { comparison ->
                Text("${comparison.added} added / ${comparison.removed} removed lines")
                comparison.lines.take(200).forEach { line ->
                    val prefix=when(line.change) {
                        dev.fusion.documents.LineChange.SAME -> "  "
                        dev.fusion.documents.LineChange.REMOVED -> "− "
                        dev.fusion.documents.LineChange.ADDED -> "+ "
                    }
                    Text(prefix+line.text.take(1024).replace("\r","␍").replace("\n","␊\n"))
                }
            }
            val activeText = state.session.documents.find { it.id == state.session.selectedId }?.content.orEmpty()
            val galleyPreview = remember(activeText) {
                val blocks = activeText.split("\n\n").filter { it.isNotBlank() }.take(32).mapIndexed { idx, p ->
                    val trimmed = p.trimStart()
                    if (trimmed.startsWith("#")) {
                        WidowOrphanGalleyPaginator.GalleyBlock.Heading("h$idx", trimmed.trimStart('#', ' '))
                    } else {
                        WidowOrphanGalleyPaginator.GalleyBlock.Paragraph("p$idx", p.chunked(70).take(24).ifEmpty { listOf("") })
                    }
                }
                val pages = if (blocks.isEmpty()) 1 else WidowOrphanGalleyPaginator.paginate(blocks).pages.size
                val parsedBib = CitationBibliographyFormatter.parseBibTex(activeText.take(65536))
                "R51 Galley preview: $pages page(s) (widow/orphan guarded) · ${parsedBib.size} BibTeX entry(ies)"
            }
            Text(galleyPreview, Modifier.testTag("editor-r51-galley-preview"))
            if(state.toolsBusy) CircularProgressIndicator()
            Text(inputError ?: state.status,Modifier.semantics { liveRegion=LiveRegionMode.Polite })
        } },confirmButton={ TextButton(onClick=onDismiss) { Text("Close tools") } })
    state.replacement?.let { plan ->
        AlertDialog(onDismissRequest=model::cancelTools,title={ Text("Apply ${plan.count} literal replacements?") },
            text={ Text("Result: ${plan.resultingBytes} UTF-8 bytes. Changes only this draft, in one undo step. Original files and any frozen Save a Copy payload stay untouched. If the draft changed since review, this operation will be refused.") },
            confirmButton={ TextButton(onClick=model::confirmReplace,modifier=Modifier.testTag("editor-confirm-replace")) { Text("Apply to draft") } },
            dismissButton={ TextButton(onClick=model::cancelTools) { Text("Keep draft unchanged") } })
    }
}
