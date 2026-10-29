// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Clean-room OASIS OpenDocument v1.3 (`.odt`) `content.xml` structural extractor for `:format:lok`.
 *
 * Self-contained inside `dev.fusion.lok` (zero Android / JNI / LibreOffice native binary
 * dependency) so `.odt` documents have a fast, deterministic, host-verifiable text & structure
 * path even when the LibreOfficeKit native `.so` is absent.
 *
 * Hardened against classic ODF XML amplification bombs:
 * - Refuses `<!DOCTYPE`, `<!ENTITY`, `<?` (except `<?xml ` prolog),
 * - Caps `table:number-columns-repeated` (`<= MAX_COL_REPEAT`),
 * - Caps `table:number-rows-repeated` (`<= MAX_ROW_REPEAT`),
 * - Caps `text:s text:c` space repetition (`<= MAX_SPACE_REPEAT`),
 * - Caps total blocks, table cells, XML depth, and expanded ZIP bytes.
 */
object OdtExtract {
    const val SCOPE =
        "OASIS ODF 1.3 .odt content.xml structural extraction (headings, paragraphs, nested lists, tables) with repetition-bomb caps; styles, images, formulas, and ODT write-back out of scope."

    const val MAX_XML_BYTES = 2 * 1024 * 1024
    const val MAX_ZIP_BYTES = 16 * 1024 * 1024
    const val MAX_DEPTH = 48
    const val MAX_BLOCKS = 4_096
    const val MAX_CELLS = 16_384
    const val MAX_COL_REPEAT = 256
    const val MAX_ROW_REPEAT = 64
    const val MAX_SPACE_REPEAT = 256

    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class ListItem(val depth: Int, val text: String) : Block
        data class Table(val name: String, val rows: List<List<String>>) : Block
    }

    data class OdtDocument(val mimeType: String?, val blocks: List<Block>) {
        fun plainText(): String = blocks.joinToString("\n") { b ->
            when (b) {
                is Block.Heading -> b.text
                is Block.Paragraph -> b.text
                is Block.ListItem -> "${"  ".repeat((b.depth - 1).coerceAtLeast(0))}- ${b.text}"
                is Block.Table -> b.rows.joinToString("\n") { it.joinToString("\t") }
            }
        }
    }

    fun readOdtZip(odtBytes: ByteArray): OdtDocument {
        require(odtBytes.size <= MAX_ZIP_BYTES) { "ODT archive exceeds $MAX_ZIP_BYTES bytes" }
        var mime: String? = null
        var contentXml: String? = null
        var totalExpanded = 0L

        ZipInputStream(ByteArrayInputStream(odtBytes)).use { zis ->
            var entries = 0
            val buf = ByteArray(8192)
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entries++
                require(entries <= 512) { "too many ODT ZIP entries" }
                val name = entry.name
                require(name.isNotEmpty() && !name.startsWith("/") && '\\' !in name && ".." !in name.split('/')) {
                    "unsafe ODT entry '$name'"
                }
                if (!entry.isDirectory) {
                    val want = name == "mimetype" || name == "content.xml"
                    val baos = if (want) ByteArrayOutputStream() else null
                    var partBytes = 0
                    while (true) {
                        val n = zis.read(buf)
                        if (n < 0) break
                        partBytes += n
                        totalExpanded += n
                        require(totalExpanded <= MAX_ZIP_BYTES) { "ODT expanded ZIP budget exceeded" }
                        if (want) {
                            require(partBytes <= MAX_XML_BYTES) { "ODT part '$name' exceeds $MAX_XML_BYTES bytes" }
                            baos!!.write(buf, 0, n)
                        }
                    }
                    if (want) {
                        val text = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(baos!!.toByteArray()))
                            .toString()
                        if (name == "mimetype") mime = text.trim() else contentXml = text
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (mime != null && mime != "application/vnd.oasis.opendocument.text") {
            throw IllegalArgumentException("unsupported ODF mimetype '$mime'")
        }
        val xml = contentXml ?: throw IllegalArgumentException("missing content.xml in ODT archive")
        return parseContentXml(xml, mime)
    }

    fun parseContentXml(xml: String, mimeType: String? = "application/vnd.oasis.opendocument.text"): OdtDocument {
        require(xml.length <= MAX_XML_BYTES) { "content.xml exceeds $MAX_XML_BYTES chars" }
        val blocks = ArrayList<Block>()
        var listDepth = 0
        var inListItem = false
        var tableDepth = 0
        var tableName = ""
        val tableRows = ArrayList<List<String>>()
        var curRowRepeat = 1
        val rowCells = ArrayList<String>()
        var inCell = false
        var curColRepeat = 1
        val cellBuf = StringBuilder()

        var inTextBlock = false
        var headingLevel = 0
        val textBuf = StringBuilder()
        var totalCells = 0

        scanSafeXml(xml, object : XmlSink {
            override fun start(qName: String, attrs: Map<String, String>) {
                val local = qName.substringAfter(':', qName)
                when (local) {
                    "list" -> listDepth++
                    "list-item" -> inListItem = true
                    "table" -> {
                        tableDepth++
                        if (tableDepth == 1) {
                            tableName = attrLocal(attrs, "name") ?: "Table"
                            tableRows.clear()
                        }
                    }
                    "table-row" -> if (tableDepth == 1) {
                        val rep = (attrLocal(attrs, "number-rows-repeated")?.toIntOrNull() ?: 1)
                        require(rep in 1..MAX_ROW_REPEAT) {
                            "table:number-rows-repeated $rep outside 1..$MAX_ROW_REPEAT"
                        }
                        curRowRepeat = rep
                        rowCells.clear()
                    }
                    "table-cell", "covered-table-cell" -> if (tableDepth == 1) {
                        val rep = (attrLocal(attrs, "number-columns-repeated")?.toIntOrNull() ?: 1)
                        require(rep in 1..MAX_COL_REPEAT) {
                            "table:number-columns-repeated $rep outside 1..$MAX_COL_REPEAT"
                        }
                        inCell = local == "table-cell"
                        curColRepeat = rep
                        cellBuf.setLength(0)
                        if (local == "covered-table-cell") {
                            totalCells += rep
                            require(totalCells <= MAX_CELLS) { "ODT table cell budget exceeded" }
                            repeat(rep) { rowCells.add("") }
                        }
                    }
                    "h" -> if (tableDepth == 0) {
                        inTextBlock = true
                        headingLevel = (attrLocal(attrs, "outline-level")?.toIntOrNull() ?: 1).coerceIn(1, 6)
                        textBuf.setLength(0)
                    }
                    "p" -> {
                        if (tableDepth == 1 && inCell) {
                            if (cellBuf.isNotEmpty()) cellBuf.append('\n')
                        } else if (tableDepth == 0) {
                            inTextBlock = true
                            headingLevel = 0
                            textBuf.setLength(0)
                        }
                    }
                    "s" -> {
                        val c = (attrLocal(attrs, "c")?.toIntOrNull() ?: 1)
                        require(c in 1..MAX_SPACE_REPEAT) { "text:s text:c=$c outside 1..$MAX_SPACE_REPEAT" }
                        val spaces = " ".repeat(c)
                        if (tableDepth == 1 && inCell) cellBuf.append(spaces)
                        else if (inTextBlock) textBuf.append(spaces)
                    }
                    "tab" -> {
                        if (tableDepth == 1 && inCell) cellBuf.append('\t')
                        else if (inTextBlock) textBuf.append('\t')
                    }
                    "line-break" -> {
                        if (tableDepth == 1 && inCell) cellBuf.append('\n')
                        else if (inTextBlock) textBuf.append('\n')
                    }
                }
            }

            override fun text(chars: String) {
                if (tableDepth == 1 && inCell) cellBuf.append(chars)
                else if (inTextBlock) textBuf.append(chars)
            }

            override fun end(qName: String) {
                val local = qName.substringAfter(':', qName)
                when (local) {
                    "h", "p" -> if (tableDepth == 0 && inTextBlock) {
                        require(blocks.size < MAX_BLOCKS) { "ODT exceeds $MAX_BLOCKS blocks" }
                        val str = textBuf.toString()
                        val block = when {
                            headingLevel > 0 -> Block.Heading(headingLevel, str)
                            inListItem && listDepth > 0 -> Block.ListItem(listDepth, str)
                            else -> Block.Paragraph(str)
                        }
                        blocks.add(block)
                        inTextBlock = false
                        headingLevel = 0
                    }
                    "list-item" -> inListItem = false
                    "list" -> listDepth = maxOf(0, listDepth - 1)
                    "table-cell" -> if (tableDepth == 1 && inCell) {
                        val cellText = cellBuf.toString()
                        totalCells += curColRepeat
                        require(totalCells <= MAX_CELLS) { "ODT table cell budget exceeded" }
                        repeat(curColRepeat) { rowCells.add(cellText) }
                        inCell = false
                    }
                    "table-row" -> if (tableDepth == 1 && rowCells.isNotEmpty()) {
                        // Trim trailing repeated blank filler cells beyond 1 empty trailing cell if whole tail is blank, ONLY when > 1 repeated at end? Wait: keep exact cells so small repeated cells stay exact!
                        val snapshot = rowCells.toList()
                        repeat(curRowRepeat) { tableRows.add(snapshot) }
                    }
                    "table" -> {
                        if (tableDepth == 1 && tableRows.isNotEmpty()) {
                            require(blocks.size < MAX_BLOCKS) { "ODT exceeds $MAX_BLOCKS blocks" }
                            blocks.add(Block.Table(tableName, tableRows.toList()))
                        }
                        tableDepth = maxOf(0, tableDepth - 1)
                    }
                }
            }
        })
        return OdtDocument(mimeType, blocks)
    }

    private fun attrLocal(attrs: Map<String, String>, local: String): String? =
        attrs[local] ?: attrs.entries.firstOrNull { it.key.substringAfter(':', it.key) == local }?.value

    private interface XmlSink {
        fun start(qName: String, attrs: Map<String, String>)
        fun text(chars: String)
        fun end(qName: String)
    }

    private fun scanSafeXml(src: String, sink: XmlSink) {
        val stack = ArrayList<String>()
        var i = 0
        val n = src.length
        if (src.startsWith("\uFEFF")) i = 1
        while (i < n && src[i].isWhitespace()) i++
        if (src.startsWith("<?xml", i)) {
            val endPi = src.indexOf("?>", i + 5)
            require(endPi >= 0) { "unclosed XML declaration" }
            i = endPi + 2
        }
        while (i < n) {
            val lt = src.indexOf('<', i)
            if (lt < 0) {
                val tail = decodeEntities(src.substring(i))
                if (stack.isNotEmpty() && tail.isNotEmpty()) sink.text(tail)
                else require(tail.all { it.isWhitespace() }) { "trailing text outside root" }
                break
            }
            if (lt > i) {
                val chunk = decodeEntities(src.substring(i, lt))
                if (stack.isNotEmpty()) sink.text(chunk)
                else require(chunk.all { it.isWhitespace() }) { "text outside root element" }
            }
            require(lt + 1 < n) { "dangling '<'" }
            when (src[lt + 1]) {
                '!' -> {
                    require(src.startsWith("<!--", lt)) { "DTD, CDATA, and ENTITY declarations are refused" }
                    val endComment = src.indexOf("-->", lt + 4)
                    require(endComment >= 0) { "unclosed XML comment" }
                    i = endComment + 3
                }
                '?' -> throw IllegalArgumentException("processing instructions outside XML prolog refused")
                '/' -> {
                    val gt = src.indexOf('>', lt + 2)
                    require(gt > lt + 2) { "malformed closing tag" }
                    val name = src.substring(lt + 2, gt).trim()
                    require(stack.isNotEmpty() && stack.last() == name) { "mismatched closing tag </$name>" }
                    stack.removeAt(stack.size - 1)
                    sink.end(name)
                    i = gt + 1
                }
                else -> {
                    var p = lt + 1
                    while (p < n && !src[p].isWhitespace() && src[p] != '/' && src[p] != '>') p++
                    val name = src.substring(lt + 1, p)
                    require(name.isNotEmpty()) { "empty element name" }
                    val attrs = LinkedHashMap<String, String>()
                    var selfClosing = false
                    while (true) {
                        while (p < n && src[p].isWhitespace()) p++
                        require(p < n) { "unclosed tag <$name>" }
                        if (src[p] == '>') { p++; break }
                        if (src[p] == '/') {
                            require(p + 1 < n && src[p + 1] == '>') { "malformed self-closing <$name/>" }
                            selfClosing = true; p += 2; break
                        }
                        val kStart = p
                        while (p < n && !src[p].isWhitespace() && src[p] != '=' && src[p] != '>' && src[p] != '/') p++
                        val key = src.substring(kStart, p)
                        while (p < n && src[p].isWhitespace()) p++
                        require(p < n && src[p] == '=') { "attribute '$key' missing '='" }
                        p++
                        while (p < n && src[p].isWhitespace()) p++
                        require(p < n && (src[p] == '"' || src[p] == '\'')) { "unquoted attribute '$key'" }
                        val quote = src[p++]
                        val vEnd = src.indexOf(quote, p)
                        require(vEnd >= 0) { "unclosed attribute '$key'" }
                        require(key !in attrs) { "duplicate attribute '$key'" }
                        attrs[key] = decodeEntities(src.substring(p, vEnd))
                        p = vEnd + 1
                    }
                    require(stack.size < MAX_DEPTH) { "XML nesting exceeds $MAX_DEPTH" }
                    sink.start(name, attrs)
                    if (selfClosing) sink.end(name) else stack.add(name)
                    i = p
                }
            }
        }
        require(stack.isEmpty()) { "unclosed XML elements: $stack" }
    }

    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] != '&') {
                sb.append(s[i++]); continue
            }
            val semi = s.indexOf(';', i + 1)
            require(semi > i + 1 && semi - i <= 12) { "malformed XML entity at $i" }
            val ent = s.substring(i + 1, semi)
            when (ent) {
                "lt" -> sb.append('<')
                "gt" -> sb.append('>')
                "amp" -> sb.append('&')
                "quot" -> sb.append('"')
                "apos" -> sb.append('\'')
                else -> if (ent.startsWith("#x") || ent.startsWith("#X")) {
                    val cp = ent.substring(2).toIntOrNull(16) ?: throw IllegalArgumentException("bad hex entity &$ent;")
                    require(cp in 0x9..0x10FFFF && cp !in 0xD800..0xDFFF) { "illegal code point &$ent;" }
                    sb.appendCodePoint(cp)
                } else if (ent.startsWith("#")) {
                    val cp = ent.substring(1).toIntOrNull(10) ?: throw IllegalArgumentException("bad dec entity &$ent;")
                    require(cp in 0x9..0x10FFFF && cp !in 0xD800..0xDFFF) { "illegal code point &$ent;" }
                    sb.appendCodePoint(cp)
                } else {
                    throw IllegalArgumentException("external/named XML entity &$ent; refused")
                }
            }
            i = semi + 1
        }
        return sb.toString()
    }
}
