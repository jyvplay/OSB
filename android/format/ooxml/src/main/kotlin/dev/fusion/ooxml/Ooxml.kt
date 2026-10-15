package dev.fusion.ooxml

import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
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
 * Use it for search, preview and plain-text export only. ZIP budgets are not process isolation,
 * an XML validator, central-directory authentication or a wall-clock/blocked-stream watchdog.
 */
object Ooxml {

    const val MAX_PART_BYTES = 32 * 1024 * 1024
    const val MAX_ENTRIES = 5_000
    const val MAX_EXPANDED_BYTES = 64L * 1024 * 1024
    const val MAX_INPUT_BYTES = 64L * 1024 * 1024
    const val MAX_XML_BYTES = 2 * 1024 * 1024
    const val MAX_CELLS = 20_000
    const val MAX_ROWS = 10_000
    const val MAX_SHARED_STRINGS = 20_000

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
        // Count actual reads, not attacker-controlled ZIP metadata. Includes unwanted/directory entries.
        var compressed=0L
        val bounded=object: InputStream() {
            override fun read(): Int {
                val value=input.read()
                if(value>=0) { compressed++;require(compressed<=MAX_INPUT_BYTES) { "ZIP input budget exceeded" } }
                return value
            }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if(length==0) return 0
                val n=input.read(bytes,offset,minOf(length.toLong(),MAX_INPUT_BYTES-compressed+1).toInt())
                require(n!=0) { "Input made no progress" }
                if(n>0) { compressed+=n;require(compressed<=MAX_INPUT_BYTES) { "ZIP input budget exceeded" } }
                return n
            }
            override fun close()=input.close()
        }
        ZipInputStream(bounded).use { zip ->
            val names=HashSet<String>()
            var total=0L;val buffer=ByteArray(8192)
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                require(names.size<MAX_ENTRIES) { "Archive has too many entries" }
                val name=entry.name
                require(name.length in 1..1024 && !name.startsWith("/") && '\\' !in name &&
                    name.none { it.code<32 || it.code==127 } &&
                    name.removeSuffix("/").split('/').none { it==".." || it=="." || it.isEmpty() }) { "Unsafe part name" }
                require(names.add(name)) { "Duplicate part name: $name" }
                val selected=!entry.isDirectory && want(name)
                val limit=if(selected) MAX_XML_BYTES else MAX_PART_BYTES
                val bytes=if(selected) ByteArrayOutputStream() else null
                var count=0L
                while(true) {
                    val n=zip.read(buffer,0,minOf(buffer.size.toLong(),limit-count+1,MAX_EXPANDED_BYTES-total+1).toInt())
                    if(n<0) break
                    require(n>0) { "ZIP decoder made no progress" }
                    count+=n;total+=n
                    require(count<=limit && total<=MAX_EXPANDED_BYTES) { "Expanded ZIP budget exceeded" }
                    bytes?.write(buffer,0,n)
                }
                if(bytes!=null) {
                    val xml=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
                    require(!xml.contains("<!DOCTYPE") && !xml.contains("<!ENTITY")) { "DTD/entity declarations unsupported" }
                    out[name]=xml
                }
                // Fully consumed through the counters: closeEntry cannot invisibly drain an ignored bomb.
                zip.closeEntry();entry=zip.nextEntry
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
                    require(end>=0) { "Unclosed text run" }
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
            require(out.size<MAX_SHARED_STRINGS) { "Too many shared strings" }
            out.add(sb.toString())
            i = siEnd + 5
        }
        return out
    }

    /** Reads <row><c r=".." t="s"><v>..</v></c></row>; "s" means shared-string index. */
    internal fun parseSheetXml(xml: String, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var rowStart = 0
        var cellCount=0
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
                if (!(attrs == "c" || attrs == "c/" || attrs.startsWith("c "))) { c = cGt + 1; continue }
                val isShared = Regex("t\\s*=\\s*\"s\"").containsMatchIn(attrs)
                require(++cellCount<=MAX_CELLS) { "Too many cells" }
                val selfClosing = attrs.endsWith("/")
                if (selfClosing) {
                    cells.add("")
                    c = cGt + 1
                    continue
                }
                val cClose = xml.indexOf("</c>", cGt)
                require(cClose>=0 && cClose<rEnd) { "Unclosed cell" }
                val limit = cClose
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
            require(rows.size<MAX_ROWS) { "Too many rows" }
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
