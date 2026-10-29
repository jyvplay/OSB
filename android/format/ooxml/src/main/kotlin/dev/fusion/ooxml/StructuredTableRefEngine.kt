package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room ECMA-376 Excel Structured Table Reference expander and OOXML `xl/tables/tableN.xml`
 * round-trip serializer/reader (inspired by IronCalc 2026 clean-room architecture; Apache-2.0).
 *
 * Supports:
 * - `Table1[Revenue]`, `Table1[[#Data],[Revenue]]` -> data body column range
 * - `Table1[#All]`, `Table1[#Data]`, `Table1[#Headers]`, `Table1[#Totals]`
 * - `Table1[@Revenue]` / `Table1[[#This Row],[Revenue]]` -> row-intersected single-cell reference
 * - `Table1[[Region]:[Profit]]` -> contiguous multi-column span
 * - Deterministic `1980-01-01T00:00:00Z` `.xlsx` package with `xl/tables/table1.xml`, `<autoFilter>`,
 *   and `SUBTOTAL(109, ...)` / `SUBTOTAL(101, ...)` totals row formulas.
 */
object StructuredTableRefEngine {
    const val SCOPE =
        "ECMA-376 Structured Table reference parser/expander ([#All]/[#Data]/[#Headers]/[#Totals]/[@Col]/[[Col1]:[Col2]]) + OOXML xl/tables/table1.xml writer/reader"

    enum class TotalsFunction(val subtotalCode: Int, val xmlName: String) {
        NONE(0, "none"),
        SUM(109, "sum"),
        AVERAGE(101, "average"),
        COUNT(103, "count"),
        MIN(105, "min"),
        MAX(104, "max"),
    }

    data class TableColumnSpec(
        val name: String,
        val totalsFn: TotalsFunction = TotalsFunction.NONE,
        val totalsLabel: String? = null,
    ) {
        init {
            require(name.isNotBlank()) { "Table column name must not be blank" }
        }
    }

    data class StructuredTable(
        val id: Int,
        val name: String,
        val topLeftA1: String,
        val columns: List<TableColumnSpec>,
        val dataRows: List<List<FormulaEval.CellInput>>,
        val includeTotalsRow: Boolean = false,
        val autoFilter: Boolean = true,
    ) {
        val origin: SheetRange.Cell = SheetRange.parseCell(topLeftA1)
        val headerRow1: Int get() = origin.row
        val firstDataRow1: Int get() = origin.row + 1
        val lastDataRow1: Int get() = origin.row + dataRows.size
        val totalsRow1: Int? get() = if (includeTotalsRow) lastDataRow1 + 1 else null
        val bottomRow1: Int get() = totalsRow1 ?: lastDataRow1
        val leftCol1: Int get() = origin.col
        val rightCol1: Int get() = origin.col + columns.size - 1

        init {
            require(id >= 1) { "Table id must be >= 1" }
            require(NAME_RE.matches(name)) { "Invalid Excel table name '$name'" }
            require(columns.isNotEmpty()) { "Table '$name' requires >= 1 column" }
            require(dataRows.isNotEmpty()) { "Table '$name' requires >= 1 data row" }
            require(columns.map { it.name.lowercase() }.distinct().size == columns.size) {
                "Duplicate column names in table '$name'"
            }
            for (r in dataRows) {
                require(r.size == columns.size) { "Data row width ${r.size} != ${columns.size}" }
            }
        }

        fun columnIndex(colName: String): Int {
            val idx = columns.indexOfFirst { it.name.equals(colName.trim(), ignoreCase = true) }
            require(idx >= 0) { "Unknown column '$colName' in table '$name'" }
            return idx
        }

        fun boundingRefA1(): String =
            "${SheetRange.Cell(leftCol1, headerRow1).toA1()}:${SheetRange.Cell(rightCol1, bottomRow1).toA1()}"
    }

    data class ParsedTableXmlSummary(
        val id: Int,
        val name: String,
        val refA1: String,
        val totalsRowCount: Int,
        val hasAutoFilter: Boolean,
        val columnNames: List<String>,
        val columnTotalsFns: List<String>,
    )

    private val NAME_RE = Regex("^[A-Za-z_][A-Za-z0-9_.]*$")
    private val STRUCT_TOKEN_RE = Regex("([A-Za-z_][A-Za-z0-9_.]*)\\[(?:\\[[^\\]]*\\]|[^\\[\\]])*\\]")
    private val BARE_ROW_TOKEN_RE = Regex("\\[@([^\\[\\]]+)\\]")

    /**
     * Rewrites all structured references in [formula] into canonical A1 cell/range coordinates
     * against [tables] (ignoring tokens inside double-quoted string literals).
     */
    fun expandFormula(
        formula: String,
        tables: List<StructuredTable>,
        currentCellA1: String? = null,
    ): String {
        val byName = tables.associateBy { it.name.lowercase() }
        val currentCell = currentCellA1?.let { SheetRange.parseCell(it) }
        val currentRow0 = currentCell?.row
        val enclosingTable = currentCell?.let { cc ->
            tables.firstOrNull { t ->
                cc.col in t.leftCol1..t.rightCol1 && cc.row in t.firstDataRow1..t.lastDataRow1
            }
        }
        val sb = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < formula.length) {
            val ch = formula[i]
            if (ch == '"') {
                sb.append(ch)
                if (inQuotes && i + 1 < formula.length && formula[i + 1] == '"') {
                    sb.append('"')
                    i += 2
                    continue
                }
                inQuotes = !inQuotes
                i++
                continue
            }
            if (!inQuotes && ch == '[' && i + 1 < formula.length && formula[i + 1] == '@') {
                val bm = BARE_ROW_TOKEN_RE.matchAt(formula, i)
                if (bm != null) {
                    val tbl = enclosingTable
                        ?: throw IllegalArgumentException("Bare structured row reference '${bm.value}' requires currentCellA1 inside a table data row")
                    val bracketBody = bm.value.substring(1, bm.value.length - 1)
                    sb.append(resolveSpecifier(tbl, bracketBody, currentRow0))
                    i = bm.range.last + 1
                    continue
                }
            }
            if (!inQuotes && (ch.isLetter() || ch == '_')) {
                val m = STRUCT_TOKEN_RE.matchAt(formula, i)
                if (m != null) {
                    val tblName = m.groupValues[1]
                    val tbl = byName[tblName.lowercase()]
                        ?: throw IllegalArgumentException("Unknown structured table '$tblName'")
                    val bracketBody = m.value.substring(tblName.length + 1, m.value.length - 1)
                    sb.append(resolveSpecifier(tbl, bracketBody, currentRow0))
                    i = m.range.last + 1
                    continue
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    /** Materializes [tables] + [extraCells] onto a single sheet and evaluates via [FormulaEval]. */
    fun evaluateSheetWithTables(
        tables: List<StructuredTable>,
        extraCells: Map<String, FormulaEval.CellInput> = emptyMap(),
    ): FormulaEval.EvaluatedSheet {
        val cells = LinkedHashMap<String, FormulaEval.CellInput>()
        for (tbl in tables) {
            for ((cIdx, col) in tbl.columns.withIndex()) {
                cells[SheetRange.Cell(tbl.leftCol1 + cIdx, tbl.headerRow1).toA1()] =
                    FormulaEval.CellInput.TextLiteral(col.name)
            }
            for ((rIdx, row) in tbl.dataRows.withIndex()) {
                val r1 = tbl.firstDataRow1 + rIdx
                for ((cIdx, cellInp) in row.withIndex()) {
                    val posA1 = SheetRange.Cell(tbl.leftCol1 + cIdx, r1).toA1()
                    cells[posA1] = if (cellInp is FormulaEval.CellInput.Formula) {
                        FormulaEval.CellInput.Formula(expandFormula(cellInp.body, tables, posA1))
                    } else {
                        cellInp
                    }
                }
            }
            val totR = tbl.totalsRow1
            if (totR != null) {
                for ((cIdx, col) in tbl.columns.withIndex()) {
                    val posA1 = SheetRange.Cell(tbl.leftCol1 + cIdx, totR).toA1()
                    val dataStart = SheetRange.Cell(tbl.leftCol1 + cIdx, tbl.firstDataRow1).toA1()
                    val dataEnd = SheetRange.Cell(tbl.leftCol1 + cIdx, tbl.lastDataRow1).toA1()
                    cells[posA1] = when {
                        col.totalsFn != TotalsFunction.NONE ->
                            FormulaEval.CellInput.Formula("=SUBTOTAL(${col.totalsFn.subtotalCode},$dataStart:$dataEnd)")
                        col.totalsLabel != null ->
                            FormulaEval.CellInput.TextLiteral(col.totalsLabel)
                        else -> FormulaEval.CellInput.Blank
                    }
                }
            }
        }
        for ((a1, inp) in extraCells) {
            val normA1 = SheetRange.parseCell(a1).toA1()
            cells[normA1] = if (inp is FormulaEval.CellInput.Formula) {
                FormulaEval.CellInput.Formula(expandFormula(inp.body, tables, normA1))
            } else {
                inp
            }
        }
        return FormulaEval.evaluateSheet(cells)
    }

    /** Writes a clean-room `.xlsx` archive containing `xl/tables/table1.xml` and evaluated worksheet values. */
    fun writeTableXlsx(table: StructuredTable): ByteArray {
        val evaluated = evaluateSheetWithTables(listOf(table))
        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            append("<sheetData>")
            for (r1 in table.headerRow1..table.bottomRow1) {
                append("""<row r="$r1">""")
                for (c1 in table.leftCol1..table.rightCol1) {
                    val a1 = SheetRange.Cell(c1, r1).toA1()
                    val v = evaluated.values[a1] ?: FormulaEval.CellValue.Empty
                    when (v) {
                        is FormulaEval.CellValue.Num -> append("""<c r="$a1"><v>${FormulaEval.formatCanonicalNumber(v.value)}</v></c>""")
                        is FormulaEval.CellValue.Text -> append("""<c r="$a1" t="inlineStr"><is><t>${esc(v.value)}</t></is></c>""")
                        is FormulaEval.CellValue.Bool -> append("""<c r="$a1" t="b"><v>${if (v.value) 1 else 0}</v></c>""")
                        else -> {}
                    }
                }
                append("</row>")
            }
            append("</sheetData>")
            append("""<tableParts count="1"><tablePart r:id="rTbl1"/></tableParts>""")
            append("</worksheet>")
        }
        val filterEndRow1 = table.lastDataRow1
        val autoFilterRef = "${SheetRange.Cell(table.leftCol1, table.headerRow1).toA1()}:${SheetRange.Cell(table.rightCol1, filterEndRow1).toA1()}"
        val tableXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<table xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" id="${table.id}" name="${esc(table.name)}" displayName="${esc(table.name)}" ref="${table.boundingRefA1()}" totalsRowCount="${if (table.includeTotalsRow) 1 else 0}">""")
            if (table.autoFilter) append("""<autoFilter ref="$autoFilterRef"/>""")
            append("""<tableColumns count="${table.columns.size}">""")
            for ((idx, col) in table.columns.withIndex()) {
                append("""<tableColumn id="${idx + 1}" name="${esc(col.name)}"""")
                if (col.totalsFn != TotalsFunction.NONE) append(""" totalsRowFunction="${col.totalsFn.xmlName}"""")
                if (col.totalsLabel != null) append(""" totalsRowLabel="${esc(col.totalsLabel)}"""")
                append("/>")
            }
            append("</tableColumns>")
            append("""<tableStyleInfo name="TableStyleMedium2" showFirstColumn="0" showLastColumn="0" showRowStripes="1" showColumnStripes="0"/>""")
            append("</table>")
        }

        val parts = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/tables/table1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheetXml,
            "xl/worksheets/_rels/sheet1.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rTbl1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/table" Target="../tables/table1.xml"/></Relationships>""",
            "xl/tables/table1.xml" to tableXml,
        )
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            for ((path, xml) in parts) {
                val raw = xml.toByteArray(StandardCharsets.UTF_8)
                val crc = CRC32().apply { update(raw) }.value
                val ze = ZipEntry(path).apply {
                    time = 315532800000L
                    method = ZipEntry.STORED
                    size = raw.size.toLong()
                    compressedSize = raw.size.toLong()
                    this.crc = crc
                }
                zos.putNextEntry(ze)
                zos.write(raw)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun inspectTableXlsx(bytes: ByteArray): ParsedTableXmlSummary {
        var tableBytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes), StandardCharsets.UTF_8).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                require(!e.name.contains("..")) { "Unsafe ZIP entry: ${e.name}" }
                if (e.name == "xl/tables/table1.xml") tableBytes = zis.readBytes()
            }
        }
        val root = BoundedXml.parse(tableBytes ?: throw IllegalArgumentException("Missing xl/tables/table1.xml"))
        val colsNode = root.children.firstOrNull { it.name.local == "tableColumns" }
            ?: throw IllegalArgumentException("Missing tableColumns in xl/tables/table1.xml")
        val cols = colsNode.children.filter { it.name.local == "tableColumn" }
        return ParsedTableXmlSummary(
            id = root.attr("id")?.toIntOrNull() ?: 0,
            name = root.attr("name") ?: "",
            refA1 = root.attr("ref") ?: "",
            totalsRowCount = root.attr("totalsRowCount")?.toIntOrNull() ?: 0,
            hasAutoFilter = root.children.any { it.name.local == "autoFilter" },
            columnNames = cols.map { it.attr("name") ?: "" },
            columnTotalsFns = cols.map { it.attr("totalsRowFunction") ?: "none" },
        )
    }

    private fun resolveSpecifier(tbl: StructuredTable, rawBody: String, currentRow1: Int?): String {
        val body = rawBody.trim()
        if (body.startsWith("@")) {
            val colIdx = tbl.columnIndex(body.removePrefix("@"))
            val r1 = requireThisRow(tbl, currentRow1)
            return SheetRange.Cell(tbl.leftCol1 + colIdx, r1).toA1()
        }
        if (!body.startsWith("[")) {
            return when (body.uppercase()) {
                "#ALL" -> rangeStr(tbl.headerRow1, tbl.leftCol1, tbl.bottomRow1, tbl.rightCol1)
                "#DATA" -> rangeStr(tbl.firstDataRow1, tbl.leftCol1, tbl.lastDataRow1, tbl.rightCol1)
                "#HEADERS" -> rangeStr(tbl.headerRow1, tbl.leftCol1, tbl.headerRow1, tbl.rightCol1)
                "#TOTALS" -> {
                    val tr = tbl.totalsRow1 ?: throw IllegalArgumentException("Table '${tbl.name}' has no #Totals row")
                    rangeStr(tr, tbl.leftCol1, tr, tbl.rightCol1)
                }
                "#THIS ROW" -> {
                    val r1 = requireThisRow(tbl, currentRow1)
                    rangeStr(r1, tbl.leftCol1, r1, tbl.rightCol1)
                }
                else -> {
                    val colIdx = tbl.columnIndex(body)
                    rangeStr(tbl.firstDataRow1, tbl.leftCol1 + colIdx, tbl.lastDataRow1, tbl.leftCol1 + colIdx)
                }
            }
        }
        // Nested bracket list, e.g. [[#Totals],[Revenue]] or [[Region]:[Profit]] or [[#This Row],[Price]]
        if (body.contains("]:[")) {
            val parts = body.split("]:[")
            require(parts.size == 2) { "Malformed column span '$rawBody'" }
            val c1 = tbl.columnIndex(parts[0].removePrefix("["))
            val c2 = tbl.columnIndex(parts[1].removeSuffix("]"))
            return rangeStr(tbl.firstDataRow1, tbl.leftCol1 + minOf(c1, c2), tbl.lastDataRow1, tbl.leftCol1 + maxOf(c1, c2))
        }
        val tokens = Regex("\\[([^\\]]+)\\]").findAll(body).map { it.groupValues[1].trim() }.toList()
        require(tokens.size in 1..2) { "Unsupported structured specifier '$rawBody'" }
        if (tokens.size == 1) return resolveSpecifier(tbl, tokens[0], currentRow1)
        val selector = tokens[0].uppercase()
        val colIdx = tbl.columnIndex(tokens[1])
        val c1 = tbl.leftCol1 + colIdx
        return when (selector) {
            "#DATA" -> rangeStr(tbl.firstDataRow1, c1, tbl.lastDataRow1, c1)
            "#ALL" -> rangeStr(tbl.headerRow1, c1, tbl.bottomRow1, c1)
            "#HEADERS" -> SheetRange.Cell(c1, tbl.headerRow1).toA1()
            "#TOTALS" -> {
                val tr = tbl.totalsRow1 ?: throw IllegalArgumentException("Table '${tbl.name}' has no #Totals row")
                SheetRange.Cell(c1, tr).toA1()
            }
            "#THIS ROW" -> SheetRange.Cell(c1, requireThisRow(tbl, currentRow1)).toA1()
            else -> throw IllegalArgumentException("Unsupported structured selector '$selector'")
        }
    }

    private fun requireThisRow(tbl: StructuredTable, currentRow1: Int?): Int {
        val r = currentRow1 ?: throw IllegalArgumentException("[@Col] / [#This Row] requires currentCellA1")
        require(r in tbl.firstDataRow1..tbl.lastDataRow1) {
            "Current row $r is outside data rows ${tbl.firstDataRow1}..${tbl.lastDataRow1} of '${tbl.name}'"
        }
        return r
    }

    private fun rangeStr(r1: Int, c1: Int, r2: Int, c2: Int): String =
        "${SheetRange.Cell(c1, r1).toA1()}:${SheetRange.Cell(c2, r2).toA1()}"

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
