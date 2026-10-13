package dev.fusion.ooxml

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Minimal, dependency-free OOXML **text extraction**.
 *
 * This is our own scanner over the parts inside the .docx/.xlsx zip. It is NOT
 * Apache POI and NOT LibreOffice. Explicit non-goals:
 *   - no layout, styles, fonts, images, charts, comments or revisions
 *   - no formulas (only cached cell values are read)
 *   - no editing and no saving back to OOXML
 * Use it for search, preview and plain-text export only.
 */
object Ooxml {

    const val MAX_PART_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRIES = 5_000

    data class Sheet(val name: String, val rows: List<List<String>>)

    fun isDocx(name: String) = name.endsWith(".docx", ignoreCase = true)
    fun isXlsx(name: String) = name.endsWith(".xlsx", ignoreCase = true)

    fun extractDocxText(input: InputStream): Result<String> = runCatching {
        val xml = readPart(input) { it == "word/document.xml" }
            ?: throw IllegalArgumentException("Not a Word document (word/document.xml missing)")
        parseDocumentXml(xml)
    }

    fun extractXlsxSheet(input: InputStream): Result<Sheet> = runCatching {
        val parts = readParts(input) { name ->
            name == "xl/sharedStrings.xml" || name == "xl/worksheets/sheet1.xml"
        }
        val sheetXml = parts["xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("Not a workbook (xl/worksheets/sheet1.xml missing)")
        val shared = parts["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        Sheet("sheet1", parseSheetXml(sheetXml, shared))
    }

    fun extractDocxText(file: File): Result<String> = file.inputStream().use(::extractDocxText)
    fun extractXlsxSheet(file: File): Result<Sheet> = file.inputStream().use(::extractXlsxSheet)

    // ---- zip reading -------------------------------------------------------

    private fun readPart(input: InputStream, want: (String) -> Boolean): String? =
        readParts(input, want).values.firstOrNull()

    private fun readParts(input: InputStream, want: (String) -> Boolean): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            var seen = 0
            while (entry != null) {
                if (++seen > MAX_ENTRIES) throw IllegalArgumentException("Archive has too many entries")
                val name = entry.name
                if (!entry.isDirectory && want(name)) {
                    val bytes = zip.readNBytes(MAX_PART_BYTES + 1)
                    if (bytes.size > MAX_PART_BYTES) throw IllegalArgumentException("Part $name is too large")
                    out[name] = bytes.toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    // ---- scanners ----------------------------------------------------------

    /** Collects <w:t> runs; </w:p> and <w:br/> become newlines. */
    internal fun parseDocumentXml(xml: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < xml.length) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            val gt = xml.indexOf('>', lt + 1)
            if (gt < 0) break
            val tag = xml.substring(lt + 1, gt)
            when {
                tag == "w:t" || tag.startsWith("w:t ") -> {
                    val end = xml.indexOf("</w:t>", gt + 1)
                    if (end >= 0) {
                        sb.append(unescape(xml.substring(gt + 1, end)))
                        i = end + 6
                        continue
                    }
                }
                tag == "/w:p" -> sb.append('\n')
                tag == "w:br" || tag == "w:br/" -> sb.append('\n')
                tag == "w:tab" || tag == "w:tab/" -> sb.append('\t')
            }
            i = gt + 1
        }
        return sb.toString().trim('\n', ' ', '\t')
    }

    /** sharedStrings.xml: ordered <si>...<t>text</t>...</si> entries. */
    internal fun parseSharedStrings(xml: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (true) {
            val si = xml.indexOf("<si", i)
            if (si < 0) break
            val siEnd = xml.indexOf("</si>", si).let { if (it < 0) xml.length else it }
            val sb = StringBuilder()
            var j = si
            while (j < siEnd) {
                val t = xml.indexOf("<t", j)
                if (t < 0 || t > siEnd) break
                val gt = xml.indexOf('>', t)
                if (gt < 0) break
                // guard against <table>-like tags: next char must close or be a space
                val tagBody = xml.substring(t + 1, gt)
                if (tagBody == "t" || tagBody.startsWith("t ")) {
                    val close = xml.indexOf("</t>", gt)
                    if (close < 0 || close > siEnd) break
                    sb.append(unescape(xml.substring(gt + 1, close)))
                    j = close + 4
                } else {
                    j = gt + 1
                }
            }
            out.add(sb.toString())
            i = siEnd + 5
        }
        return out
    }

    /** Reads <row><c r=".." t="s"><v>..</v></c></row>; "s" means shared-string index. */
    internal fun parseSheetXml(xml: String, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var rowStart = 0
        while (true) {
            val r = xml.indexOf("<row", rowStart)
            if (r < 0) break
            val rEnd = xml.indexOf("</row>", r).let { if (it < 0) xml.length else it }
            val cells = mutableListOf<String>()
            var c = r
            while (c in 0 until rEnd) {
                val cIdx = xml.indexOf("<c", c)
                if (cIdx < 0 || cIdx > rEnd) break
                val cGt = xml.indexOf('>', cIdx)
                if (cGt < 0) break
                val attrs = xml.substring(cIdx + 1, cGt)
                if (!(attrs == "c" || attrs.startsWith("c "))) { c = cGt + 1; continue }
                val isShared = Regex("t\\s*=\\s*\"s\"").containsMatchIn(attrs)
                val selfClosing = attrs.endsWith("/")
                if (selfClosing) {
                    cells.add("")
                    c = cGt + 1
                    continue
                }
                val cClose = xml.indexOf("</c>", cGt)
                val limit = if (cClose < 0) rEnd else cClose
                val vIdx = xml.indexOf("<v>", cGt)
                var value = ""
                if (vIdx in 0 until limit) {
                    val vClose = xml.indexOf("</v>", vIdx)
                    if (vClose in 0..limit) {
                        val raw = unescape(xml.substring(vIdx + 3, vClose))
                        value = if (isShared) raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: raw else raw
                    }
                }
                cells.add(value)
                c = if (cClose < 0) cGt + 1 else cClose + 4
            }
            rows.add(cells)
            rowStart = rEnd + 6
        }
        return rows
    }

    internal fun unescape(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")
}
