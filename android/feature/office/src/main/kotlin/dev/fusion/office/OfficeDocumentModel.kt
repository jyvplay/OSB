// SPDX-License-Identifier: Apache-2.0
package dev.fusion.office

import dev.fusion.poi.CellValue
import dev.fusion.poi.CleanRoomPoiEngine
import dev.fusion.poi.CleanWorkbook
import dev.fusion.poi.DocxDocument
import dev.fusion.poi.DocxParagraph
import dev.fusion.poi.SheetCell
import dev.fusion.poi.SlideDeck
import dev.fusion.poi.WorkbookSheet
import java.util.Collections

/**
 * Pure immutable state and undoable revision engine for the Apache-2.0 Clean-Room Office Workbench (`:feature:office`).
 *
 * Bridges `:format:poi` across three modes (`WRITER`, `CALC_GRID`, `SLIDES`) without native WebView `SharedArrayBuffer`
 * dependencies, keeping macros strictly disabled and recording explicit fidelity notes on every import/export.
 */
enum class OfficeTab(val label: String) { WRITER("Document"), CALC_GRID("Spreadsheet"), SLIDES("Presentation") }

data class OfficeState(
    val activeTab: OfficeTab = OfficeTab.CALC_GRID,
    val revision: Long = 0L,
    val docTitle: String = "Fusion Architecture Brief",
    val docBody: String = "Local-first Apache-2.0 clean-room document draft.\nAll macros and external XML entities are disabled.",
    val sheetCells: Map<String, SheetCell> = defaultCells(),
    val slides: List<SlideDeck.SlidePage> = defaultSlides(),
    val status: String = "Ready · clean-room Apache-2.0 OOXML engine (macros disabled, bounded layout)"
) {
    fun evaluatedCells(): Map<String, CellValue> {
        val wb = CleanWorkbook(listOf(WorkbookSheet("Sheet1", sheetCells)))
        return CleanRoomPoiEngine.evaluateWorkbook(wb).getValue("Sheet1")
    }

    fun exportDocxBytes(): ByteArray {
        val paras = buildList {
            add(DocxParagraph(docTitle.ifBlank { "Untitled" }, headingLevel = 1))
            docBody.split('\n').forEach { add(DocxParagraph(it)) }
        }
        return CleanRoomPoiEngine.writeDocx(DocxDocument(paras))
    }

    fun exportXlsxBytes(): ByteArray =
        CleanRoomPoiEngine.writeXlsx(CleanWorkbook(listOf(WorkbookSheet("Sheet1", sheetCells))))

    fun exportPptxBytes(): ByteArray =
        CleanRoomPoiEngine.writePptx(SlideDeck(slides))

    companion object {
        fun defaultCells(): Map<String, SheetCell> = Collections.unmodifiableMap(
            linkedMapOf(
                "A1" to SheetCell("A1", CellValue.Number(120.0)),
                "A2" to SheetCell("A2", CellValue.Number(80.0)),
                "A3" to SheetCell("A3", CellValue.Number(50.0)),
                "B1" to SheetCell("B1", CellValue.Blank, "SUM(A1:A3)"),
                "B2" to SheetCell("B2", CellValue.Blank, "ROUND(AVERAGE(A1:A3), 2)")
            )
        )

        fun defaultSlides(): List<SlideDeck.SlidePage> = Collections.unmodifiableList(
            listOf(
                SlideDeck.SlidePage(1, "Fusion Office Workbench", listOf("Apache-2.0 clean-room DOCX / XLSX / PPTX", "Zero macros · zero telemetry")),
                SlideDeck.SlidePage(2, "Engine Portfolio", listOf("Pure JVM POI engine on every device", "Separate LOK WASM experiment for print layout"))
            )
        )
    }
}

class OfficeSessionController(initial: OfficeState = OfficeState()) {
    private val undoStack = ArrayDeque<OfficeState>()
    private val redoStack = ArrayDeque<OfficeState>()
    var state: OfficeState = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun commit(next: OfficeState) {
        if (undoStack.size >= 64) undoStack.removeFirst()
        undoStack.addLast(state)
        redoStack.clear()
        state = next.copy(revision = state.revision + 1)
    }

    fun selectTab(tab: OfficeTab) {
        state = state.copy(activeTab = tab)
    }

    fun updateCellInput(rawRef: String, rawInput: String) {
        val ref = CleanRoomPoiEngine.canonicalRef(rawRef)
        require(rawInput.length <= 4096) { "Cell input exceeds 4,096 chars" }
        val updated = LinkedHashMap(state.sheetCells)
        val trimmed = rawInput.trim()
        val cell = when {
            trimmed.isEmpty() -> SheetCell(ref, CellValue.Blank, null)
            trimmed.startsWith("=") -> SheetCell(ref, CellValue.Blank, trimmed.removePrefix("="))
            trimmed.equals("true", true) || trimmed.equals("false", true) ->
                SheetCell(ref, CellValue.Bool(trimmed.equals("true", true)), null)
            trimmed.toDoubleOrNull()?.isFinite() == true ->
                SheetCell(ref, CellValue.Number(trimmed.toDouble()), null)
            else -> SheetCell(ref, CellValue.Text(rawInput), null)
        }
        updated[ref] = cell
        val preview = CleanRoomPoiEngine.evaluateWorkbook(CleanWorkbook(listOf(WorkbookSheet("Sheet1", updated)))).getValue("Sheet1")[ref]
        commit(
            state.copy(
                sheetCells = Collections.unmodifiableMap(updated),
                status = "Updated $ref -> ${formatCellValue(preview ?: CellValue.Blank)}"
            )
        )
    }

    fun updateDocument(title: String, body: String) {
        require(title.length <= 256 && body.length <= 262_144) { "Document input exceeds bounded draft size" }
        commit(state.copy(docTitle = title, docBody = body, status = "Document draft updated (${body.length} chars)"))
    }

    fun addSlide(title: String, bulletLine: String) {
        require(state.slides.size < 256 && title.isNotBlank() && title.length <= 160) { "Invalid slide title or deck full" }
        val nextSlides = state.slides + SlideDeck.SlidePage(
            state.slides.size + 1,
            title.trim(),
            bulletLine.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        )
        commit(state.copy(slides = Collections.unmodifiableList(nextSlides), status = "Added slide ${nextSlides.size}"))
    }

    fun importOoxmlBytes(fileName: String, bytes: ByteArray) {
        val lower = fileName.lowercase()
        when {
            lower.endsWith(".docx") -> {
                val parsed = CleanRoomPoiEngine.readDocx(bytes)
                val heading = parsed.paragraphs.firstOrNull { it.headingLevel > 0 }?.text ?: fileName
                val body = parsed.paragraphs.drop(if (parsed.paragraphs.firstOrNull()?.headingLevel ?: 0 > 0) 1 else 0)
                    .joinToString("\n") { it.text }
                commit(state.copy(activeTab = OfficeTab.WRITER, docTitle = heading, docBody = body, status = "Imported DOCX (${parsed.paragraphs.size} paragraphs; macros refused)"))
            }
            lower.endsWith(".xlsx") -> {
                val wb = CleanRoomPoiEngine.readXlsx(bytes)
                val first = wb.sheets.first()
                commit(state.copy(activeTab = OfficeTab.CALC_GRID, sheetCells = first.cells, status = "Imported XLSX sheet '${first.name}' (${first.cells.size} cells; macros refused)"))
            }
            lower.endsWith(".pptx") -> {
                val deck = CleanRoomPoiEngine.readPptx(bytes)
                commit(state.copy(activeTab = OfficeTab.SLIDES, slides = deck.slides, status = "Imported PPTX (${deck.slides.size} slides; macros refused)"))
            }
            else -> throw IllegalArgumentException("Supported clean-room Office formats: .docx, .xlsx, .pptx")
        }
    }

    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(state)
        state = prev
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(state)
        state = next
        return true
    }

    companion object {
        fun formatCellValue(v: CellValue): String = when (v) {
            CellValue.Blank -> ""
            is CellValue.Text -> v.value
            is CellValue.Bool -> v.value.toString().uppercase()
            is CellValue.Error -> v.code
            is CellValue.Number -> if (v.value == v.value.toLong().toDouble()) v.value.toLong().toString() else v.value.toString()
        }
    }
}
