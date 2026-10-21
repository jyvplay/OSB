// SPDX-License-Identifier: Apache-2.0
package dev.fusion.poi

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Clean-room Apache-2.0 OOXML Office Engine (`:format:poi`) for `.docx`, `.xlsx`, and `.pptx`.
 *
 * Architectural role:
 * Provides a fast, memory-safe, 100% Apache-2.0 JVM/Android engine alongside the separate MPL-2.0 LibreOfficeKit WASM
 * worker experiment (`experiments/lok-wasm`), avoiding WebView `SharedArrayBuffer` blockers on Android WebView while
 * refusing VBA macros (`vbaProject.bin`), XXE external entities, and zip-bomb decompression attacks.
 *
 * Explicit boundary: Supports structured paragraphs/headings/tables in `.docx`, multi-sheet typed values + pure
 * side-effect-free formula evaluation (`SUM`, `AVERAGE`, `MIN`, `MAX`, `COUNT`, `IF`, `ROUND`, `ABS`, `+ - * / ^`)
 * in `.xlsx`, and multi-slide text decks in `.pptx`. Does NOT render arbitrary print page layout, charts, pivot tables,
 * SmartArt, OLE embeddings, or macros.
 */
object CleanRoomPoiLimits {
    const val MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
    const val MAX_UNCOMPRESSED_BYTES = 32 * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 2_048
    const val MAX_FORMULA_DEPTH = 32
    const val MAX_SHEETS = 32
    const val MAX_ROWS = 4_096
    const val MAX_COLS = 256
}

data class DocxParagraph(val text: String, val style: String = "Normal", val headingLevel: Int = 0)
data class DocxDocument(
    val paragraphs: List<DocxParagraph>,
    val tables: List<List<List<String>>> = emptyList(),
    val warnings: List<String> = emptyList()
)

sealed interface CellValue {
    data object Blank : CellValue
    data class Text(val value: String) : CellValue
    data class Number(val value: Double) : CellValue
    data class Bool(val value: Boolean) : CellValue
    data class Error(val code: String) : CellValue
}

data class SheetCell(
    val ref: String,
    val raw: CellValue,
    val formula: String? = null
)

data class WorkbookSheet(
    val name: String,
    val cells: Map<String, SheetCell>
) {
    init {
        require(name.isNotBlank() && name.length <= 31 && name.none { it in setOf('\\', '/', '*', '?', ':', '[', ']') }) {
            "Invalid worksheet name: $name"
        }
    }
}

data class CleanWorkbook(
    val sheets: List<WorkbookSheet>,
    val warnings: List<String> = emptyList()
) {
    init {
        require(sheets.size in 1..CleanRoomPoiLimits.MAX_SHEETS) { "Workbook requires 1..32 sheets" }
        require(sheets.map { it.name.lowercase(Locale.ROOT) }.distinct().size == sheets.size) { "Duplicate sheet name" }
    }
}

data class SlideDeck(
    val slides: List<SlidePage>,
    val warnings: List<String> = emptyList()
) {
    data class SlidePage(val number: Int, val title: String, val bullets: List<String>)
    init {
        require(slides.size in 1..256) { "Presentation requires 1..256 slides" }
    }
}

object CleanRoomPoiEngine {
    private fun secureXml(bytes: ByteArray): Document {
        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return dbf.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun readSafeZip(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size in 22..CleanRoomPoiLimits.MAX_ARCHIVE_BYTES) { "Archive size out of bounds: ${bytes.size}" }
        val entries = LinkedHashMap<String, ByteArray>()
        var totalUncompressed = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                require(entries.size < CleanRoomPoiLimits.MAX_ZIP_ENTRIES) { "Too many ZIP entries" }
                val name = entry.name
                require(!name.startsWith("/") && ".." !in name.split('/') && '\\' !in name) { "Unsafe ZIP entry: $name" }
                require(!name.endsWith("vbaProject.bin", ignoreCase = true) && !name.endsWith(".bin", ignoreCase = true).let {
                    it && "vba" in name.lowercase(Locale.ROOT)
                }) { "Macro-enabled OOXML (`vbaProject.bin`) is strictly refused" }
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zis.read(buf)
                        if (n < 0) break
                        totalUncompressed += n
                        require(totalUncompressed <= CleanRoomPoiLimits.MAX_UNCOMPRESSED_BYTES) {
                            "ZIP decompression ratio / size ceiling exceeded"
                        }
                        out.write(buf, 0, n)
                    }
                    require(name !in entries) { "Duplicate ZIP entry refused: $name" }
                    entries[name] = out.toByteArray()
                }
            }
        }
        require(entries.isNotEmpty()) { "Empty archive" }
        return entries
    }

    private fun xmlEscape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20) append(ch)
            }
        }
    }

    private fun childrenByLocal(node: Node, local: String): List<Element> {
        val list = ArrayList<Element>()
        val nl = node.childNodes
        for (i in 0 until nl.length) {
            val item = nl.item(i)
            if (item is Element && (item.localName == local || item.tagName.substringAfter(':') == local)) {
                list.add(item)
            }
        }
        return list
    }

    private fun descendantsByLocal(root: Node, local: String): List<Element> {
        val list = ArrayList<Element>()
        fun walk(current: Node) {
            val nl = current.childNodes
            for (i in 0 until nl.length) {
                val item = nl.item(i)
                if (item is Element) {
                    if (item.localName == local || item.tagName.substringAfter(':') == local) list.add(item)
                    walk(item)
                }
            }
        }
        walk(root)
        return list
    }

    // ---- DOCX ----

    fun readDocx(bytes: ByteArray): DocxDocument {
        val entries = readSafeZip(bytes)
        val xml = requireNotNull(entries["word/document.xml"]) { "Missing word/document.xml" }
        val doc = secureXml(xml)
        val body = descendantsByLocal(doc, "body").firstOrNull() ?: error("Missing w:body")
        val paragraphs = ArrayList<DocxParagraph>()
        val tables = ArrayList<List<List<String>>>()
        val warnings = ArrayList<String>()
        if (entries.keys.any { it.startsWith("word/media/") }) {
            warnings.add("Embedded media preserved only when unmodified; visual image layout omitted.")
        }
        for (child in childrenByLocal(body, "p")) {
            val pPr = childrenByLocal(child, "pPr").firstOrNull()
            val styleEl = pPr?.let { childrenByLocal(it, "pStyle").firstOrNull() }
            val style = styleEl?.attributes?.let { attrs ->
                (0 until attrs.length).map { attrs.item(it) }.firstOrNull { it.localName == "val" || it.nodeName.endsWith("val") }?.nodeValue
            } ?: "Normal"
            val heading = Regex("Heading([1-6])", RegexOption.IGNORE_CASE).matchEntire(style)?.groupValues?.get(1)?.toInt() ?: 0
            val text = buildString {
                for (el in descendantsByLocal(child, "t")) append(el.textContent)
            }
            paragraphs.add(DocxParagraph(text, style, heading))
        }
        for (tbl in childrenByLocal(body, "tbl")) {
            val rows = childrenByLocal(tbl, "tr").map { tr ->
                childrenByLocal(tr, "tc").map { tc ->
                    descendantsByLocal(tc, "t").joinToString("") { it.textContent }
                }
            }
            if (rows.isNotEmpty()) tables.add(rows)
        }
        return DocxDocument(
            Collections.unmodifiableList(paragraphs),
            Collections.unmodifiableList(tables),
            Collections.unmodifiableList(warnings)
        )
    }

    fun writeDocx(document: DocxDocument): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            )
            val bodyXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
                for (p in document.paragraphs) {
                    val effectiveStyle = if (p.headingLevel in 1..6) "Heading${p.headingLevel}" else p.style
                    append("""<w:p><w:pPr><w:pStyle w:val="${xmlEscape(effectiveStyle)}"/></w:pPr>""")
                    append("""<w:r><w:t xml:space="preserve">${xmlEscape(p.text)}</w:t></w:r></w:p>""")
                }
                for (table in document.tables) {
                    append("<w:tbl>")
                    for (row in table) {
                        append("<w:tr>")
                        for (cell in row) {
                            append("""<w:tc><w:p><w:r><w:t xml:space="preserve">${xmlEscape(cell)}</w:t></w:r></w:p></w:tc>""")
                        }
                        append("</w:tr>")
                    }
                    append("</w:tbl>")
                }
                append("</w:body></w:document>")
            }
            put("word/document.xml", bodyXml)
        }
        return out.toByteArray()
    }

    // ---- XLSX ----

    fun colIndexToName(col0: Int): String {
        require(col0 in 0 until CleanRoomPoiLimits.MAX_COLS) { "Column out of bounds: $col0" }
        var n = col0 + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun parseCellRef(ref: String): Pair<Int, Int> {
        val clean = ref.replace("$", "").uppercase(Locale.ROOT)
        val m = requireNotNull(Regex("^([A-Z]{1,3})([1-9][0-9]{0,4})$").matchEntire(clean)) { "Invalid cell ref: $ref" }
        var col = 0
        for (ch in m.groupValues[1]) col = col * 26 + (ch.code - 'A'.code + 1)
        val col0 = col - 1
        val row0 = m.groupValues[2].toInt() - 1
        require(col0 in 0 until CleanRoomPoiLimits.MAX_COLS && row0 in 0 until CleanRoomPoiLimits.MAX_ROWS) {
            "Cell ref exceeds bounded grid: $ref"
        }
        return row0 to col0
    }

    fun canonicalRef(ref: String): String {
        val (r, c) = parseCellRef(ref)
        return "${colIndexToName(c)}${r + 1}"
    }

    fun readXlsx(bytes: ByteArray): CleanWorkbook {
        val entries = readSafeZip(bytes)
        val sharedStrings = ArrayList<String>()
        entries["xl/sharedStrings.xml"]?.let { sstBytes ->
            val sstDoc = secureXml(sstBytes)
            for (si in descendantsByLocal(sstDoc, "si")) {
                sharedStrings.add(descendantsByLocal(si, "t").joinToString("") { it.textContent })
            }
        }
        val relMap = LinkedHashMap<String, String>()
        entries["xl/_rels/workbook.xml.rels"]?.let { relBytes ->
            val relDoc = secureXml(relBytes)
            for (rel in descendantsByLocal(relDoc, "Relationship")) {
                val id = rel.getAttribute("Id")
                val target = rel.getAttribute("Target").removePrefix("/").let {
                    if (it.startsWith("xl/")) it else "xl/$it"
                }
                if (id.isNotEmpty()) relMap[id] = target
            }
        }
        val wbBytes = requireNotNull(entries["xl/workbook.xml"]) { "Missing xl/workbook.xml" }
        val wbDoc = secureXml(wbBytes)
        val sheetEls = descendantsByLocal(wbDoc, "sheet")
        require(sheetEls.isNotEmpty() && sheetEls.size <= CleanRoomPoiLimits.MAX_SHEETS) { "Invalid sheet count" }
        val sheets = sheetEls.mapIndexed { idx, el ->
            val name = el.getAttribute("name").ifBlank { "Sheet${idx + 1}" }
            val rId = (0 until el.attributes.length)
                .map { el.attributes.item(it) }
                .firstOrNull { it.localName == "id" || it.nodeName == "r:id" }
                ?.nodeValue
            val sheetPath = (rId?.let { relMap[it] } ?: "xl/worksheets/sheet${idx + 1}.xml")
            val sheetXml = requireNotNull(entries[sheetPath]) { "Missing worksheet $sheetPath" }
            val sDoc = secureXml(sheetXml)
            val cells = LinkedHashMap<String, SheetCell>()
            for (rowEl in descendantsByLocal(sDoc, "row")) {
                val rowNum = rowEl.getAttribute("r").toIntOrNull() ?: 1
                var fallbackCol = 0
                for (cEl in childrenByLocal(rowEl, "c")) {
                    val rawRef = cEl.getAttribute("r").ifBlank { "${colIndexToName(fallbackCol)}$rowNum" }
                    val ref = canonicalRef(rawRef)
                    fallbackCol = parseCellRef(ref).second + 1
                    val type = cEl.getAttribute("t")
                    val formula = childrenByLocal(cEl, "f").firstOrNull()?.textContent?.trim()?.removePrefix("=")
                    val vText = childrenByLocal(cEl, "v").firstOrNull()?.textContent
                    val inlineText = childrenByLocal(cEl, "is").firstOrNull()?.let {
                        descendantsByLocal(it, "t").joinToString("") { t -> t.textContent }
                    }
                    val parsedValue: CellValue = when (type) {
                        "s" -> {
                            val sIdx = vText?.trim()?.toIntOrNull() ?: error("Invalid shared string index")
                            require(sIdx in sharedStrings.indices) { "Shared string index out of range: $sIdx" }
                            CellValue.Text(sharedStrings[sIdx])
                        }
                        "inlineStr" -> CellValue.Text(inlineText ?: "")
                        "str" -> CellValue.Text(vText ?: "")
                        "b" -> CellValue.Bool(vText?.trim() == "1" || vText.equals("true", true))
                        "e" -> CellValue.Error(vText ?: "#ERR!")
                        else -> {
                            if (vText.isNullOrBlank()) CellValue.Blank
                            else vText.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let { CellValue.Number(it) }
                                ?: CellValue.Text(vText)
                        }
                    }
                    cells[ref] = SheetCell(ref, parsedValue, formula)
                }
            }
            WorkbookSheet(name, Collections.unmodifiableMap(cells))
        }
        return CleanWorkbook(Collections.unmodifiableList(sheets))
    }

    fun writeXlsx(workbook: CleanWorkbook): ByteArray {
        val evaluated = evaluateWorkbook(workbook)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            val overrides = workbook.sheets.indices.joinToString("\n") { i ->
                """  <Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
$overrides
</Types>"""
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
${workbook.sheets.indices.joinToString("\n") { i ->
    """  <Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>"""
}}
</Relationships>"""
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
${workbook.sheets.mapIndexed { i, s -> """    <sheet name="${xmlEscape(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("\n")}
  </sheets>
</workbook>"""
            )
            workbook.sheets.forEachIndexed { idx, sheet ->
                val evalMap = evaluated.getValue(sheet.name)
                val byRow = sheet.cells.values
                    .groupBy { parseCellRef(it.ref).first }
                    .toSortedMap()
                val sheetXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                    for ((row0, rowCells) in byRow) {
                        append("""<row r="${row0 + 1}">""")
                        for (cell in rowCells.sortedBy { parseCellRef(it.ref).second }) {
                            val computed = evalMap[cell.ref] ?: cell.raw
                            val fTag = cell.formula?.let { "<f>${xmlEscape(it.removePrefix("="))}</f>" } ?: ""
                            when (computed) {
                                is CellValue.Number -> append("""<c r="${cell.ref}">${fTag}<v>${computed.value}</v></c>""")
                                is CellValue.Bool -> append("""<c r="${cell.ref}" t="b">${fTag}<v>${if (computed.value) 1 else 0}</v></c>""")
                                is CellValue.Error -> append("""<c r="${cell.ref}" t="e">${fTag}<v>${xmlEscape(computed.code)}</v></c>""")
                                is CellValue.Text -> {
                                    if (fTag.isNotEmpty()) {
                                        append("""<c r="${cell.ref}" t="str">${fTag}<v>${xmlEscape(computed.value)}</v></c>""")
                                    } else {
                                        append("""<c r="${cell.ref}" t="inlineStr"><is><t xml:space="preserve">${xmlEscape(computed.value)}</t></is></c>""")
                                    }
                                }
                                CellValue.Blank -> if (fTag.isNotEmpty()) append("""<c r="${cell.ref}">${fTag}</c>""")
                            }
                        }
                        append("</row>")
                    }
                    append("</sheetData></worksheet>")
                }
                put("xl/worksheets/sheet${idx + 1}.xml", sheetXml)
            }
        }
        return out.toByteArray()
    }

    /** Deterministic side-effect-free formula evaluator with strict cycle and recursion-depth detection. */
    fun evaluateWorkbook(workbook: CleanWorkbook): Map<String, Map<String, CellValue>> {
        val sheetByName = workbook.sheets.associateBy { it.name }
        val memo = HashMap<Pair<String, String>, CellValue>()
        val visiting = HashSet<Pair<String, String>>()

        fun evalCell(sheetName: String, rawRef: String, depth: Int): CellValue {
            if (depth > CleanRoomPoiLimits.MAX_FORMULA_DEPTH) return CellValue.Error("#DEPTH!")
            val sheet = sheetByName[sheetName] ?: return CellValue.Error("#REF!")
            val ref = runCatching { canonicalRef(rawRef) }.getOrElse { return CellValue.Error("#REF!") }
            val key = sheetName to ref
            memo[key]?.let { return it }
            if (!visiting.add(key)) return CellValue.Error("#CYCLE!")
            val cell = sheet.cells[ref]
            val result = try {
                if (cell == null) CellValue.Blank
                else if (cell.formula == null) cell.raw
                else FormulaParser(cell.formula.removePrefix("=").trim(), sheetName, depth) { targetSheet, targetRef, nextDepth ->
                    evalCell(targetSheet, targetRef, nextDepth)
                }.parse()
            } finally {
                visiting.remove(key)
            }
            memo[key] = result
            return result
        }

        return workbook.sheets.associate { sheet ->
            sheet.name to sheet.cells.keys.associateWith { ref -> evalCell(sheet.name, ref, 0) }
        }
    }

    // ---- PPTX ----

    fun readPptx(bytes: ByteArray): SlideDeck {
        val entries = readSafeZip(bytes)
        val slideEntries = entries.keys
            .mapNotNull { name -> Regex("^ppt/slides/slide([1-9][0-9]{0,2})\\.xml$").matchEntire(name)?.let { it.groupValues[1].toInt() to name } }
            .sortedBy { it.first }
        require(slideEntries.isNotEmpty()) { "Missing ppt/slides/slideN.xml entries" }
        val slides = slideEntries.map { (num, path) ->
            val doc = secureXml(entries.getValue(path))
            val shapes = descendantsByLocal(doc, "sp")
            val paragraphs = shapes.mapNotNull { sp ->
                val text = descendantsByLocal(sp, "t").joinToString("") { it.textContent }.trim()
                text.takeIf { it.isNotEmpty() }
            }
            val title = paragraphs.firstOrNull() ?: "Slide $num"
            val bullets = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()
            SlideDeck.SlidePage(num, title, bullets)
        }
        return SlideDeck(Collections.unmodifiableList(slides))
    }

    fun writePptx(deck: SlideDeck): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            val slideOverrides = deck.slides.indices.joinToString("\n") { i ->
                """  <Override PartName="/ppt/slides/slide${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>"""
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
$slideOverrides
</Types>"""
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
            )
            put(
                "ppt/_rels/presentation.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
${deck.slides.indices.joinToString("\n") { i ->
    """  <Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${i + 1}.xml"/>"""
}}
</Relationships>"""
            )
            put(
                "ppt/presentation.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <p:sldIdLst>
${deck.slides.indices.joinToString("\n") { i -> """    <p:sldId id="${256 + i}" r:id="rId${i + 1}"/>""" }}
  </p:sldIdLst>
</p:presentation>"""
            )
            deck.slides.forEachIndexed { idx, slide ->
                val allShapes = listOf(slide.title) + slide.bullets
                val spXml = allShapes.mapIndexed { sIdx, line ->
                    """<p:sp><p:nvSpPr><p:cNvPr id="${sIdx + 2}" name="Shape ${sIdx + 1}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>${xmlEscape(line)}</a:t></a:r></a:p></p:txBody></p:sp>"""
                }.joinToString("")
                put(
                    "ppt/slides/slide${idx + 1}.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree>$spXml</p:spTree></p:cSld></p:sld>"""
                )
            }
        }
        return out.toByteArray()
    }
}

private class FormulaParser(
    private val input: String,
    private val currentSheet: String,
    private val depth: Int,
    private val lookup: (sheet: String, ref: String, nextDepth: Int) -> CellValue
) {
    private var pos = 0

    private fun skipWs() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    fun parse(): CellValue {
        val result = parseComparison()
        skipWs()
        return if (pos == input.length) result else CellValue.Error("#SYNTAX!")
    }

    private fun toNum(v: CellValue): Double? = when (v) {
        CellValue.Blank -> 0.0
        is CellValue.Number -> v.value
        is CellValue.Bool -> if (v.value) 1.0 else 0.0
        is CellValue.Text -> v.value.toDoubleOrNull()
        is CellValue.Error -> null
    }

    private fun compareValues(a: CellValue, b: CellValue): Int {
        val na = toNum(a); val nb = toNum(b)
        if (na != null && nb != null) return na.compareTo(nb)
        val sa = when (a) { is CellValue.Text -> a.value; is CellValue.Bool -> a.value.toString(); else -> "" }
        val sb = when (b) { is CellValue.Text -> b.value; is CellValue.Bool -> b.value.toString(); else -> "" }
        return sa.compareTo(sb, ignoreCase = true)
    }

    private fun parseComparison(): CellValue {
        val left = parseAddSub()
        if (left is CellValue.Error) return left
        skipWs()
        val op = listOf("<=", ">=", "<>", "=", "<", ">").firstOrNull { input.startsWith(it, pos) } ?: return left
        pos += op.length
        val right = parseAddSub()
        if (right is CellValue.Error) return right
        val cmp = compareValues(left, right)
        val ok = when (op) {
            "<=" -> cmp <= 0
            ">=" -> cmp >= 0
            "<>" -> cmp != 0
            "=" -> cmp == 0
            "<" -> cmp < 0
            else -> cmp > 0
        }
        return CellValue.Bool(ok)
    }

    private fun parseAddSub(): CellValue {
        var acc = parseMulDiv()
        while (acc !is CellValue.Error) {
            skipWs()
            if (pos >= input.length || (input[pos] != '+' && input[pos] != '-')) break
            val op = input[pos++]
            val rhs = parseMulDiv()
            if (rhs is CellValue.Error) return rhs
            val a = toNum(acc) ?: return CellValue.Error("#VALUE!")
            val b = toNum(rhs) ?: return CellValue.Error("#VALUE!")
            val v = if (op == '+') a + b else a - b
            acc = if (v.isFinite()) CellValue.Number(v) else CellValue.Error("#NUM!")
        }
        return acc
    }

    private fun parseMulDiv(): CellValue {
        var acc = parsePow()
        while (acc !is CellValue.Error) {
            skipWs()
            if (pos >= input.length || (input[pos] != '*' && input[pos] != '/')) break
            val op = input[pos++]
            val rhs = parsePow()
            if (rhs is CellValue.Error) return rhs
            val a = toNum(acc) ?: return CellValue.Error("#VALUE!")
            val b = toNum(rhs) ?: return CellValue.Error("#VALUE!")
            if (op == '/' && b == 0.0) return CellValue.Error("#DIV/0!")
            val v = if (op == '*') a * b else a / b
            acc = if (v.isFinite()) CellValue.Number(v) else CellValue.Error("#NUM!")
        }
        return acc
    }

    private fun parsePow(): CellValue {
        val base = parseUnary()
        if (base is CellValue.Error) return base
        skipWs()
        if (pos < input.length && input[pos] == '^') {
            pos++
            val exp = parsePow()
            if (exp is CellValue.Error) return exp
            val a = toNum(base) ?: return CellValue.Error("#VALUE!")
            val b = toNum(exp) ?: return CellValue.Error("#VALUE!")
            val v = a.pow(b)
            return if (v.isFinite()) CellValue.Number(v) else CellValue.Error("#NUM!")
        }
        return base
    }

    private fun parseUnary(): CellValue {
        skipWs()
        if (pos < input.length && (input[pos] == '-' || input[pos] == '+')) {
            val neg = input[pos++] == '-'
            val inner = parseUnary()
            if (inner is CellValue.Error) return inner
            val n = toNum(inner) ?: return CellValue.Error("#VALUE!")
            return CellValue.Number(if (neg) -n else n)
        }
        return parsePrimary()
    }

    private fun expandRange(sheet: String, startRef: String, endRef: String): List<CellValue> {
        val (r1, c1) = CleanRoomPoiEngine.parseCellRef(startRef)
        val (r2, c2) = CleanRoomPoiEngine.parseCellRef(endRef)
        val rMin = minOf(r1, r2); val rMax = maxOf(r1, r2)
        val cMin = minOf(c1, c2); val cMax = maxOf(c1, c2)
        require((rMax - rMin + 1L) * (cMax - cMin + 1L) <= 4096L) { "Formula range exceeds 4,096 cells" }
        val vals = ArrayList<CellValue>()
        for (r in rMin..rMax) {
            for (c in cMin..cMax) {
                vals.add(lookup(sheet, "${CleanRoomPoiEngine.colIndexToName(c)}${r + 1}", depth + 1))
            }
        }
        return vals
    }

    private fun parsePrimary(): CellValue {
        skipWs()
        if (pos >= input.length) return CellValue.Error("#SYNTAX!")
        if (input[pos] == '(') {
            pos++
            val inner = parseComparison()
            skipWs()
            if (pos >= input.length || input[pos] != ')') return CellValue.Error("#SYNTAX!")
            pos++
            return inner
        }
        if (input[pos] == '"') {
            pos++
            val sb = StringBuilder()
            while (pos < input.length) {
                val c = input[pos++]
                if (c == '"') {
                    if (pos < input.length && input[pos] == '"') { sb.append('"'); pos++ } else return CellValue.Text(sb.toString())
                } else sb.append(c)
            }
            return CellValue.Error("#SYNTAX!")
        }
        val numMatch = Regex("^[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?").find(input.substring(pos))
        if (numMatch != null) {
            pos += numMatch.value.length
            return numMatch.value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { CellValue.Number(it) } ?: CellValue.Error("#NUM!")
        }
        val tokenMatch = Regex("^([A-Za-z0-9_\\$]+!)??([A-Za-z_\\$][A-Za-z0-9_\\$]*)").find(input.substring(pos))
            ?: return CellValue.Error("#SYNTAX!")
        pos += tokenMatch.value.length
        val explicitSheet = tokenMatch.groupValues[1].takeIf { it.isNotEmpty() }?.removeSuffix("!")
        val ident = tokenMatch.groupValues[2]
        skipWs()
        if (explicitSheet == null && pos < input.length && input[pos] == '(') {
            pos++
            return parseFunction(ident.uppercase(Locale.ROOT))
        }
        if (explicitSheet == null && ident.equals("TRUE", true)) return CellValue.Bool(true)
        if (explicitSheet == null && ident.equals("FALSE", true)) return CellValue.Bool(false)
        return lookup(explicitSheet ?: currentSheet, ident, depth + 1)
    }

    private fun parseFunction(fn: String): CellValue {
        val args = ArrayList<List<CellValue>>()
        skipWs()
        if (pos < input.length && input[pos] == ')') {
            pos++
        } else {
            while (true) {
                skipWs()
                val rangeMatch = Regex("^([A-Za-z0-9_\\$]+!)?(\\$?[A-Za-z]{1,3}\\$?[1-9][0-9]{0,4})\\s*:\\s*(\\$?[A-Za-z]{1,3}\\$?[1-9][0-9]{0,4})")
                    .find(input.substring(pos))
                if (rangeMatch != null) {
                    pos += rangeMatch.value.length
                    val sh = rangeMatch.groupValues[1].takeIf { it.isNotEmpty() }?.removeSuffix("!") ?: currentSheet
                    val expanded = runCatching { expandRange(sh, rangeMatch.groupValues[2], rangeMatch.groupValues[3]) }
                        .getOrElse { return CellValue.Error("#REF!") }
                    args.add(expanded)
                } else {
                    args.add(listOf(parseComparison()))
                }
                skipWs()
                if (pos < input.length && input[pos] == ',') { pos++; continue }
                if (pos < input.length && input[pos] == ')') { pos++; break }
                return CellValue.Error("#SYNTAX!")
            }
        }
        val flat = args.flatten()
        flat.filterIsInstance<CellValue.Error>().firstOrNull()?.let { if (fn != "IF") return it }
        val nums = flat.filterIsInstance<CellValue.Number>().map { it.value }
        return when (fn) {
            "SUM" -> CellValue.Number(nums.sum())
            "COUNT" -> CellValue.Number(nums.size.toDouble())
            "AVERAGE" -> if (nums.isEmpty()) CellValue.Error("#DIV/0!") else CellValue.Number(nums.average())
            "MIN" -> CellValue.Number(nums.minOrNull() ?: 0.0)
            "MAX" -> CellValue.Number(nums.maxOrNull() ?: 0.0)
            "ABS" -> {
                if (args.size != 1 || args[0].size != 1) return CellValue.Error("#VALUE!")
                val n = toNum(args[0][0]) ?: return CellValue.Error("#VALUE!")
                CellValue.Number(abs(n))
            }
            "ROUND" -> {
                if (args.size != 2 || args[0].size != 1 || args[1].size != 1) return CellValue.Error("#VALUE!")
                val n = toNum(args[0][0]) ?: return CellValue.Error("#VALUE!")
                val digits = (toNum(args[1][0]) ?: return CellValue.Error("#VALUE!")).toInt().coerceIn(-9, 9)
                val factor = 10.0.pow(digits)
                CellValue.Number((n * factor).roundToLong().toDouble() / factor)
            }
            "IF" -> {
                if (args.size !in 2..3 || args[0].size != 1) return CellValue.Error("#VALUE!")
                val cond = args[0][0]
                if (cond is CellValue.Error) return cond
                val truthy = when (cond) {
                    is CellValue.Bool -> cond.value
                    is CellValue.Number -> cond.value != 0.0
                    is CellValue.Text -> cond.value.isNotEmpty()
                    else -> false
                }
                if (truthy) args[1].firstOrNull() ?: CellValue.Bool(true)
                else args.getOrNull(2)?.firstOrNull() ?: CellValue.Bool(false)
            }
            else -> CellValue.Error("#NAME?")
        }
    }
}
