// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Clean-room SpreadsheetML `.xlsx` formula & cached-value workbook serializer and reader.
 *
 * Bridges [FormulaEval] and [BoundedXml]:
 * - [write] evaluates all cells via [FormulaEval.evaluateSheet] and emits a deterministic
 *   zero-epoch `.xlsx` OPC archive where formula cells carry BOTH `<f>...</f>` AND cached
 *   `<v>...</v>` (with `t="str"`, `t="b"`, `t="e"`, or numeric default per ECMA-376 §18.3.1.4).
 *   Verified against Python `openpyxl` in BOTH `data_only=False` (reads formulas) and
 *   `data_only=True` (reads cached results without launching Excel).
 * - [read] parses `.xlsx` workbooks (`xl/workbook.xml`, optional `xl/sharedStrings.xml`,
 *   `xl/worksheets/sheet1.xml`) back into [LoadedWorkbook], refusing VBA `vbaProject.bin`
 *   macro archives (`.xlsm` smuggling).
 */
object FormulaXlsx {
    const val SCOPE =
        "Single-sheet .xlsx formula (<f>) + cached value (<v>) writer and BoundedXml reader (sharedStrings + inlineStr + bool + err + number); VBA macros, styles, charts, and multi-sheet workbooks refused."

    const val SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    const val MAX_ZIP_BYTES = 16 * 1024 * 1024
    const val MAX_PART_BYTES = 1024 * 1024

    data class LoadedWorkbook(
        val sheetName: String,
        val inputs: Map<String, FormulaEval.CellInput>,
        val cachedValues: Map<String, FormulaEval.CellValue>,
    ) {
        fun recalculate(): FormulaEval.EvaluatedSheet = FormulaEval.evaluateSheet(inputs)
    }

    fun write(sheetName: String, cells: Map<String, FormulaEval.CellInput>): ByteArray {
        validateSheetName(sheetName)
        val evaluated = FormulaEval.evaluateSheet(cells)
        val canonicalInputs = LinkedHashMap<String, FormulaEval.CellInput>(cells.size)
        for ((k, v) in cells) canonicalInputs[FormulaEval.canonicalCellRef(k)] = v

        val parts = linkedMapOf(
            "[Content_Types].xml" to contentTypesXml(),
            "_rels/.rels" to rootRelsXml(),
            "xl/workbook.xml" to workbookXml(sheetName),
            "xl/_rels/workbook.xml.rels" to workbookRelsXml(),
            "xl/worksheets/sheet1.xml" to worksheetXml(canonicalInputs, evaluated.values),
        )
        return storedZip(parts)
    }

    fun read(xlsxBytes: ByteArray): LoadedWorkbook {
        require(xlsxBytes.size <= MAX_ZIP_BYTES) { "xlsx exceeds $MAX_ZIP_BYTES bytes" }
        val parts = readZipParts(xlsxBytes)
        require(parts.keys.none { it.endsWith("vbaProject.bin", ignoreCase = true) }) {
            "VBA macro part (vbaProject.bin) refused"
        }
        val wbBytes = parts["xl/workbook.xml"] ?: throw IllegalArgumentException("missing xl/workbook.xml")
        val sheetBytes = parts["xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("missing xl/worksheets/sheet1.xml")
        val sheetName = parseSheetName(wbBytes)
        val sharedStrings = parts["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val (inputs, cached) = parseSheet(sheetBytes, sharedStrings)
        return LoadedWorkbook(sheetName, inputs, cached)
    }

    private fun worksheetXml(
        inputs: Map<String, FormulaEval.CellInput>,
        values: Map<String, FormulaEval.CellValue>,
    ): String {
        val byRow = LinkedHashMap<Int, MutableList<Pair<SheetRange.Cell, String>>>()
        for (a1 in inputs.keys) {
            val c = SheetRange.parseCell(a1)
            byRow.getOrPut(c.row) { ArrayList() }.add(c to a1)
        }
        val sortedRows = byRow.keys.sorted()
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="$SHEET_NS"><sheetData>""")
            for (r in sortedRows) {
                val rowEntries = byRow[r]!!.sortedBy { it.first.col }
                append("""<row r="$r">""")
                for ((_, a1) in rowEntries) {
                    val inp = inputs[a1]!!
                    val value = values[a1] ?: FormulaEval.CellValue.Empty
                    appendCellXml(a1, inp, value)
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
    }

    private fun StringBuilder.appendCellXml(
        a1: String,
        inp: FormulaEval.CellInput,
        value: FormulaEval.CellValue,
    ) {
        if (inp is FormulaEval.CellInput.Formula) {
            val fEsc = escapeXml(inp.body)
            when (value) {
                FormulaEval.CellValue.Empty -> append("""<c r="$a1"><f>$fEsc</f></c>""")
                is FormulaEval.CellValue.Num ->
                    append("""<c r="$a1"><f>$fEsc</f><v>${FormulaEval.formatCanonicalNumber(value.value)}</v></c>""")
                is FormulaEval.CellValue.Bool ->
                    append("""<c r="$a1" t="b"><f>$fEsc</f><v>${if (value.value) 1 else 0}</v></c>""")
                is FormulaEval.CellValue.Err ->
                    append("""<c r="$a1" t="e"><f>$fEsc</f><v>${escapeXml(value.error.literal)}</v></c>""")
                is FormulaEval.CellValue.Text -> {
                    validateXmlChars(value.value)
                    append("""<c r="$a1" t="str"><f>$fEsc</f><v xml:space="preserve">${escapeXml(value.value)}</v></c>""")
                }
            }
            return
        }
        when (inp) {
            FormulaEval.CellInput.Blank -> {}
            is FormulaEval.CellInput.NumberLiteral ->
                append("""<c r="$a1"><v>${FormulaEval.formatCanonicalNumber(inp.value)}</v></c>""")
            is FormulaEval.CellInput.BoolLiteral ->
                append("""<c r="$a1" t="b"><v>${if (inp.value) 1 else 0}</v></c>""")
            is FormulaEval.CellInput.ErrorLiteral ->
                append("""<c r="$a1" t="e"><v>${escapeXml(inp.error.literal)}</v></c>""")
            is FormulaEval.CellInput.TextLiteral -> {
                validateXmlChars(inp.value)
                append("""<c r="$a1" t="inlineStr"><is><t xml:space="preserve">${escapeXml(inp.value)}</t></is></c>""")
            }
            is FormulaEval.CellInput.Formula -> error("handled above")
        }
    }

    private fun parseSheetName(wbBytes: ByteArray): String {
        val root = BoundedXml.parse(wbBytes)
        require(root.name.local == "workbook") { "expected <workbook>, got <${root.name.local}>" }
        val sheetsNode = root.children.firstOrNull { it.name.local == "sheets" }
            ?: throw IllegalArgumentException("<workbook> missing <sheets>")
        val sheetNodes = sheetsNode.children.filter { it.name.local == "sheet" }
        require(sheetNodes.size == 1) { "expected exactly 1 worksheet, found ${sheetNodes.size}" }
        return localAttr(sheetNodes[0], "name") ?: throw IllegalArgumentException("<sheet> missing name")
    }

    private fun parseSharedStrings(ssBytes: ByteArray): List<String> {
        val root = BoundedXml.parse(ssBytes)
        require(root.name.local == "sst") { "expected <sst>, got <${root.name.local}>" }
        val out = ArrayList<String>()
        for (si in root.children) {
            if (si.name.local != "si") continue
            val sb = StringBuilder()
            collectTText(si, sb)
            out.add(sb.toString())
        }
        return out
    }

    private fun collectTText(node: BoundedXml.Node, out: StringBuilder) {
        if (node.name.local == "t") {
            out.append(node.text)
            return
        }
        for (c in node.children) collectTText(c, out)
    }

    private fun parseSheet(
        sheetBytes: ByteArray,
        sharedStrings: List<String>,
    ): Pair<Map<String, FormulaEval.CellInput>, Map<String, FormulaEval.CellValue>> {
        val root = BoundedXml.parse(sheetBytes)
        require(root.name.local == "worksheet") { "expected <worksheet>, got <${root.name.local}>" }
        val sheetData = root.children.firstOrNull { it.name.local == "sheetData" }
            ?: throw IllegalArgumentException("<worksheet> missing <sheetData>")

        val inputs = LinkedHashMap<String, FormulaEval.CellInput>()
        val cached = LinkedHashMap<String, FormulaEval.CellValue>()

        for (row in sheetData.children) {
            if (row.name.local != "row") continue
            for (cNode in row.children) {
                if (cNode.name.local != "c") continue
                val rawR = localAttr(cNode, "r") ?: throw IllegalArgumentException("<c> missing r attribute")
                val ref = FormulaEval.canonicalCellRef(rawR)
                val cellType = localAttr(cNode, "t") ?: "n"

                val fNode = cNode.children.firstOrNull { it.name.local == "f" }
                if (fNode != null) {
                    val fType = localAttr(fNode, "t")
                    require(fType == null || fType == "normal") { "array/shared formula t='$fType' out of scope" }
                }
                val vNode = cNode.children.firstOrNull { it.name.local == "v" }
                val isNode = cNode.children.firstOrNull { it.name.local == "is" }
                val inlineSb = StringBuilder()
                if (isNode != null) collectTText(isNode, inlineSb)

                val decodedVal = decodeCellValue(
                    t = cellType,
                    hasV = vNode != null,
                    vText = vNode?.text ?: "",
                    inlineText = inlineSb.toString(),
                    sharedStrings = sharedStrings,
                )
                cached[ref] = decodedVal
                inputs[ref] = if (fNode != null) {
                    FormulaEval.CellInput.Formula(fNode.text)
                } else {
                    when (decodedVal) {
                        FormulaEval.CellValue.Empty -> FormulaEval.CellInput.Blank
                        is FormulaEval.CellValue.Num -> FormulaEval.CellInput.NumberLiteral(decodedVal.value)
                        is FormulaEval.CellValue.Text -> FormulaEval.CellInput.TextLiteral(decodedVal.value)
                        is FormulaEval.CellValue.Bool -> FormulaEval.CellInput.BoolLiteral(decodedVal.value)
                        is FormulaEval.CellValue.Err -> FormulaEval.CellInput.ErrorLiteral(decodedVal.error)
                    }
                }
            }
        }
        return inputs to cached
    }

    private fun localAttr(node: BoundedXml.Node, local: String): String? =
        node.attributes.entries.firstOrNull { it.key.local == local }?.value

    private fun decodeCellValue(
        t: String,
        hasV: Boolean,
        vText: String,
        inlineText: String,
        sharedStrings: List<String>,
    ): FormulaEval.CellValue = when (t) {
        "inlineStr" -> FormulaEval.CellValue.Text(inlineText)
        "s" -> {
            if (!hasV) FormulaEval.CellValue.Empty
            else {
                val idx = vText.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("bad sharedString index '$vText'")
                require(idx in sharedStrings.indices) { "sharedString index $idx out of bounds" }
                FormulaEval.CellValue.Text(sharedStrings[idx])
            }
        }
        "str" -> if (hasV) FormulaEval.CellValue.Text(vText) else FormulaEval.CellValue.Empty
        "b" -> if (!hasV) FormulaEval.CellValue.Empty else when (vText.trim()) {
            "1", "true", "TRUE" -> FormulaEval.CellValue.Bool(true)
            "0", "false", "FALSE" -> FormulaEval.CellValue.Bool(false)
            else -> throw IllegalArgumentException("bad boolean cell value '$vText'")
        }
        "e" -> if (!hasV) FormulaEval.CellValue.Empty else FormulaEval.CellValue.Err(CellErr.parse(vText.trim()))
        "n", "" -> if (!hasV || vText.isBlank()) FormulaEval.CellValue.Empty else {
            val d = vText.trim().toDoubleOrNull()
                ?: throw IllegalArgumentException("bad numeric cell value '$vText'")
            FormulaEval.CellValue.Num(d)
        }
        else -> throw IllegalArgumentException("unsupported SpreadsheetML cell type t='$t'")
    }

    private fun validateSheetName(name: String) {
        require(name.length in 1..31) { "sheet name length must be 1..31" }
        require(!name.startsWith('\'') && !name.endsWith('\'')) { "sheet name cannot start/end with apostrophe" }
        require(name.none { it in "\\/?*[]:" }) { "illegal character in sheet name '$name'" }
        validateXmlChars(name)
    }

    private fun validateXmlChars(s: String) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate()) {
                require(i + 1 < s.length && s[i + 1].isLowSurrogate()) { "unpaired surrogate" }
                i += 2; continue
            }
            require(!c.isLowSurrogate()) { "unpaired surrogate" }
            val cp = c.code
            require(cp == 0x09 || cp == 0x0A || cp == 0x0D || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD) {
                "illegal XML char U+${cp.toString(16)}"
            }
            i++
        }
    }

    private fun escapeXml(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            '\r' -> append("&#13;") // Preserve carriage returns across XML end-of-line normalization.
            else -> append(c)
        }
    }

    private fun readZipParts(zipBytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var totalExpanded = 0L
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entries = 0
            val buf = ByteArray(8192)
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entries++
                require(entries <= 256) { "too many ZIP entries" }
                val name = entry.name
                require(name.isNotEmpty() && !name.startsWith("/") && '\\' !in name && ".." !in name.split('/')) {
                    "unsafe ZIP entry '$name'"
                }
                require(name !in out) { "duplicate ZIP entry '$name'" }
                if (!entry.isDirectory) {
                    val baos = ByteArrayOutputStream()
                    var partBytes = 0
                    while (true) {
                        val n = zis.read(buf)
                        if (n < 0) break
                        partBytes += n
                        totalExpanded += n
                        require(partBytes <= MAX_PART_BYTES && totalExpanded <= MAX_ZIP_BYTES) {
                            "expanded ZIP budget exceeded on '$name'"
                        }
                        baos.write(buf, 0, n)
                    }
                    out[name] = baos.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return out
    }

    private fun storedZip(entries: Map<String, String>): ByteArray {
        val body = ByteArrayOutputStream()
        val cd = ByteArrayOutputStream()
        var count = 0
        for ((name, content) in entries) {
            val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
            val data = content.toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(data) }.value.toInt()
            val offset = body.size()
            body.write(leHeader(30) {
                putInt(0x04034b50); putShort(20); putShort(0x0800); putShort(0)
                putShort(0); putShort(0x0021); putInt(crc)
                putInt(data.size); putInt(data.size); putShort(nameBytes.size.toShort()); putShort(0)
            })
            body.write(nameBytes); body.write(data)
            cd.write(leHeader(46) {
                putInt(0x02014b50); putShort(20); putShort(20); putShort(0x0800); putShort(0)
                putShort(0); putShort(0x0021); putInt(crc)
                putInt(data.size); putInt(data.size); putShort(nameBytes.size.toShort())
                putShort(0); putShort(0); putShort(0); putShort(0); putInt(0); putInt(offset)
            })
            cd.write(nameBytes)
            count++
        }
        val cdOffset = body.size()
        val cdBytes = cd.toByteArray()
        body.write(cdBytes)
        body.write(leHeader(22) {
            putInt(0x06054b50); putShort(0); putShort(0)
            putShort(count.toShort()); putShort(count.toShort())
            putInt(cdBytes.size); putInt(cdOffset); putShort(0)
        })
        return body.toByteArray()
    }

    private fun leHeader(size: Int, fill: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()

    private fun contentTypesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            """</Types>"""

    private fun rootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun workbookXml(sheetName: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="$SHEET_NS" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets><sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private fun workbookRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            """</Relationships>"""
}
