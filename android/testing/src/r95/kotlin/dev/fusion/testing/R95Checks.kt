// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxBreaks
import dev.fusion.ooxml.XlsxHeaderFooter
import dev.fusion.ooxml.XlsxPrintSetup
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R95 spreadsheet contracts: the two tail records that follow `<pageSetup>` — `<headerFooter>`
 * (§18.3.1.46) and `<rowBreaks>`/`<colBreaks>` (§18.3.1.84, §18.3.1.11).
 *
 * Host JVM only. The probe writes real `.xlsx` packages plus two intent tables (`headerfooter.tsv`,
 * `breaks.tsv`). Independent witnesses: `tools/check_r95_sheet_header_footer.py` (raw OPC walk + openpyxl's
 * `HeaderFooter` model, including its `&L`/`&C`/`&R` section split) and `tools/check_r95_sheet_breaks.py`
 * (raw OPC walk + openpyxl's `RowBreak`/`ColBreak` models). No Excel, LibreOffice, printer or Android run:
 * nothing here says a page was laid out, that a header was drawn, or that a page break was honoured.
 */
object R95Checks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun partOrNull(bytes: ByteArray, entry: String): String? {
        var found: String? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            while (found == null) {
                val current = input.nextEntry ?: break
                val payload = input.readBytes()
                if (current.name == entry) found = text(payload)
            }
        }
        return found
    }

    private fun part(bytes: ByteArray, entry: String): String =
        partOrNull(bytes, entry) ?: error("missing OPC part $entry")

    private fun cell(ref: String, value: String) = XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    private fun header(slot: XlsxHeaderFooter.Slot, value: String) = XlsxHeaderFooter.Text(slot, value)

    private val GRID_HEADER_FOOTER = XlsxHeaderFooter.Spec(
        // Deliberately not in schema order: the writer must sort, and the lane asserts the schema order.
        texts = listOf(
            header(XlsxHeaderFooter.Slot.ODD_FOOTER, "&R&A page &P/&N"),
            header(XlsxHeaderFooter.Slot.ODD_HEADER, "&LQuarterly report&CConfidential"),
            header(XlsxHeaderFooter.Slot.EVEN_HEADER, "&CEven pages"),
        ),
        differentOddEven = true,
        scaleWithDoc = false,
        alignWithMargins = false,
    )

    private val GRID_BREAKS = XlsxBreaks.Spec(
        rows = listOf(
            XlsxBreaks.Brk(id = 10, manual = true),
            XlsxBreaks.Brk(id = 25, manual = false),
            XlsxBreaks.Brk(id = 40, manual = true, min = 0, max = 16_383),
        ),
        cols = listOf(XlsxBreaks.Brk(id = 3, manual = true)),
    )

    private val PRINT = XlsxPrintSetup.Spec(
        options = XlsxPrintSetup.Options(gridLines = true),
        margins = XlsxPrintSetup.Margins(left = 0.5, right = 0.5),
        setup = XlsxPrintSetup.Setup(orientation = XlsxPrintSetup.Orientation.LANDSCAPE, paperSize = 9, scale = 85),
    )

    /** `&L…&C…&R…` split the way Excel reads it: the marker opens a section, later codes stay in its text. */
    internal fun sections(raw: String): Map<Char, String> {
        val result = LinkedHashMap<Char, String>()
        var current: Char? = null
        var start = 0
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '&' && i + 1 < raw.length && raw[i + 1] in "LCR") {
                current?.let { result[it] = raw.substring(start, i) }
                current = raw[i + 1]
                start = i + 2
                i += 2
            } else if (raw[i] == '&' && i + 1 < raw.length) {
                i += 2
            } else {
                i++
            }
        }
        current?.let { result[it] = raw.substring(start) }
        return result
    }

    private fun tsv(value: String?): String =
        when (value) {
            null -> ""
            else -> value.replace("\t", "\\t").replace("\n", "\\n")
        }

    private fun writeIntent(folder: File, sheets: List<Pair<String, XlsxStyledBook.StyledSheet>>) {
        val headLines = ArrayList<String>()
        val breakLines = ArrayList<String>()
        for ((file, sheet) in sheets) {
            val hf = sheet.headerFooter
            if (hf == null) {
                headLines.add(listOf(file, sheet.name, "0", "-", "-", "-", "-", "-", "-", "-").joinToString("\t"))
            } else {
                for (slot in XlsxHeaderFooter.Slot.values()) {
                    val entry = hf.texts.firstOrNull { it.slot == slot }
                    headLines.add(
                        listOf(
                            file, sheet.name, "1", slot.token, if (entry == null) "0" else "1",
                            tsv(entry?.text),
                            if (hf.differentOddEven) "1" else "0",
                            if (hf.differentFirst) "1" else "0",
                            hf.scaleWithDoc?.let { if (it) "1" else "0" } ?: "-",
                            hf.alignWithMargins?.let { if (it) "1" else "0" } ?: "-",
                        ).joinToString("\t"),
                    )
                }
            }
            val breaks = sheet.breaks
            if (breaks == null) {
                breakLines.add(listOf(file, sheet.name, "0", "-", "-", "-", "-", "-").joinToString("\t"))
            } else {
                for ((axis, list) in listOf("row" to breaks.rows, "col" to breaks.cols)) {
                    for (brk in list) {
                        breakLines.add(
                            listOf(
                                file, sheet.name, axis, brk.id.toString(),
                                if (brk.manual) "1" else "0",
                                brk.min?.toString() ?: "-", brk.max?.toString() ?: "-",
                                breaks.manualCount.toString(),
                            ).joinToString("\t"),
                        )
                    }
                }
            }
        }
        File(folder, "headerfooter.tsv").writeText(headLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        File(folder, "breaks.tsv").writeText(breakLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    /** A package in openpyxl's shapes: attributes reordered, breaks unsorted, one duplicate and one stray id. */
    private object ForeignPackage {
        private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

        fun build(): ByteArray {
            val parts = linkedMapOf(
                "[Content_Types].xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
                "_rels/.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
                "xl/workbook.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Foreign" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/styles.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$MAIN"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="1"><fill><patternFill patternType="none"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs></styleSheet>""",
                "xl/worksheets/sheet1.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>foreign</t></is></c></row></sheetData>""" +
                    // openpyxl writes the flags after the child element is closed, and brk attributes as id/min/max/man.
                    """<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""" +
                    """<pageSetup paperSize="1" orientation="portrait" scale="50"/>""" +
                    """<headerFooter scaleWithDoc="0" differentFirst="1" alignWithMargins="0" differentOddEven="0">""" +
                    """<oddHeader>&amp;LForeign &amp;Bleft</oddHeader>""" +
                    """<oddFooter>&amp;Cpage &amp;P</oddFooter>""" +
                    """<firstHeader>&amp;Cfirst</firstHeader>""" +
                    """<evenHeader>&amp;Cnever used</evenHeader>""" +
                    """</headerFooter>""" +
                    """<rowBreaks count="4" manualBreakCount="3">""" +
                    """<brk id="30" max="16383" man="1"/><brk id="12" min="0" max="16383" man="0"/>""" +
                    """<brk id="12" min="0" max="16383" man="1"/><brk id="0" man="1"/><brk id="99" man="false"/>""" +
                    """</rowBreaks>""" +
                    """<colBreaks count="1" manualBreakCount="1"><brk id="5" man="1" max="1048575"/></colBreaks>""" +
                    """</worksheet>""",
            )
            val out = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { zip ->
                for ((name, xml) in parts) {
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(xml.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            return out.toByteArray()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r95/artifacts")
        folder.mkdirs()

        val grid = XlsxStyledBook.StyledSheet(
            name = "Grid",
            cells = listOf(cell("A1", "header and breaks"), cell("B2", "x")),
            print = PRINT,
            headerFooter = GRID_HEADER_FOOTER,
            breaks = GRID_BREAKS,
        )
        val first = XlsxStyledBook.StyledSheet(
            name = "First",
            cells = listOf(cell("A1", "title")),
            headerFooter = XlsxHeaderFooter.Spec(
                texts = listOf(
                    header(XlsxHeaderFooter.Slot.FIRST_HEADER, "&CTitle page"),
                    header(XlsxHeaderFooter.Slot.FIRST_FOOTER, "&RConfidential"),
                    // Escaping surface: a literal ampersand is && and a literal '<' still has to be escaped.
                    header(XlsxHeaderFooter.Slot.ODD_HEADER, "&LTom && Jerry <co>"),
                ),
                differentFirst = true,
                scaleWithDoc = true,
            ),
            breaks = XlsxBreaks.Spec(rows = listOf(XlsxBreaks.Brk(id = 5, manual = true, min = 2, max = 9))),
        )
        val plain = XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "open")))
        val book = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(listOf(grid, first, plain)),
        )

        // 1. The tail is in CT_Worksheet order and follows pageSetup.
        val sheet1 = part(book, "xl/worksheets/sheet1.xml")
        val markers = listOf("</sheetData>", "<pageMargins", "<pageSetup", "<headerFooter", "<headerFooter ",
            "<rowBreaks", "</rowBreaks>", "<colBreaks", "</colBreaks>", "</worksheet>")
        val order = markers.map { sheet1.indexOf(it) }
        check(order.all { it >= 0 }) { "a Grid tail marker is missing: $markers -> $order" }
        check(order == order.sorted()) { "the Grid tail is out of CT_Worksheet order: $markers -> $order" }

        // 2. Exact bytes for the header/footer: schema-order slots, declared flags only, defaults suppressed.
        val expectedHeaderFooter =
            """<headerFooter differentOddEven="1" scaleWithDoc="0" alignWithMargins="0">""" +
                """<oddHeader>&amp;LQuarterly report&amp;CConfidential</oddHeader>""" +
                """<oddFooter>&amp;R&amp;A page &amp;P/&amp;N</oddFooter>""" +
                """<evenHeader>&amp;CEven pages</evenHeader></headerFooter>"""
        check(sheet1.contains(expectedHeaderFooter)) {
            "unexpected <headerFooter> bytes: " + (Regex("<headerFooter.*?</headerFooter>").find(sheet1)?.value)
        }
        // 3. Exact bytes for the breaks: count and manualBreakCount are both written, spans only when declared.
        val expectedBreaks =
            """<rowBreaks count="3" manualBreakCount="2"><brk id="10" man="1"/><brk id="25" man="0"/>""" +
                """<brk id="40" min="0" max="16383" man="1"/></rowBreaks>""" +
                """<colBreaks count="1" manualBreakCount="1"><brk id="3" man="1"/></colBreaks>"""
        check(sheet1.contains(expectedBreaks)) {
            "unexpected breaks bytes: " + (Regex("<rowBreaks.*?</colBreaks>").find(sheet1)?.value)
        }

        // 4. The escaping surface: a literal '&' is &&, and XML-escaping still applies to both.
        val sheet2 = part(book, "xl/worksheets/sheet2.xml")
        check(sheet2.contains("<oddHeader>&amp;LTom &amp;&amp; Jerry &lt;co&gt;</oddHeader>")) {
            "a doubled ampersand or an escaped '<' was written wrong: " +
                (Regex("<oddHeader>.*?</oddHeader>").find(sheet2)?.value)
        }
        check(sheet2.contains("""<headerFooter differentFirst="1" scaleWithDoc="1">""")) {
            "an explicit scaleWithDoc=true was not written, or differentFirst was lost"
        }
        check(!sheet2.contains("alignWithMargins")) { "an undeclared alignWithMargins was invented" }
        check(sheet2.contains("""<rowBreaks count="1" manualBreakCount="1"><brk id="5" min="2" max="9" man="1"/>""")) {
            "a declared span was not written before man"
        }

        // 5. A sheet with neither record is byte-identical to the pre-R95 tail.
        val sheet3 = part(book, "xl/worksheets/sheet3.xml")
        check(sheet3.endsWith("</sheetData></worksheet>")) { "a plain sheet grew a tail: ${sheet3.takeLast(120)}" }
        check(!sheet3.contains("headerFooter") && !sheet3.contains("Breaks")) { "a plain sheet grew a record" }

        // 6. No new package parts: these records are attributes and children, not relationships.
        var parts = 0
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(book)).use { input ->
            while (input.nextEntry != null) {
                parts++
                input.readBytes()
            }
        }
        check(parts == 8) { "unexpected part count: $parts" }
        check(partOrNull(book, "xl/worksheets/_rels/sheet1.xml.rels") == null) {
            "a header/footer or break grew a relationship part"
        }

        // 7. Round trip: both records survive a read/write cycle byte for byte.
        val read = XlsxStyledBook.read(book)
        // `Spec.texts` keeps the caller's order; writer and reader both canonicalise through `ordered`.
        check(read.sheets[0].headerFooter?.ordered == GRID_HEADER_FOOTER.ordered) {
            "header/footer did not round-trip: ${read.sheets[0].headerFooter}"
        }
        check(read.sheets[0].headerFooter?.differentOddEven == true &&
            read.sheets[0].headerFooter?.scaleWithDoc == false &&
            read.sheets[0].headerFooter?.alignWithMargins == false) {
            "header/footer flags did not round-trip: ${read.sheets[0].headerFooter}"
        }
        check(read.sheets[0].breaks == GRID_BREAKS) { "breaks did not round-trip: ${read.sheets[0].breaks}" }
        check(read.sheets[2].headerFooter == null && read.sheets[2].breaks == null) {
            "a plain sheet read back a record it does not have"
        }
        val rewritten = XlsxStyledBook.write(read)
        check(rewritten.contentEquals(book)) { "write(read(write(spec))) is not byte-identical" }

        // 8. Section splitting is the reader's view, so the lane and the probe must agree on it.
        val sections = sections("&LQuarterly report&CConfidential")
        check(sections == mapOf('L' to "Quarterly report", 'C' to "Confidential")) {
            "section split disagrees with the emitted text: $sections"
        }
        check(sections("&Ronly").keys == setOf('R')) { "a single trailing section did not split" }

        // 9. Refusals — every one is a shape this writer must not emit.
        fun refuses(what: String, block: () -> Unit) {
            val failed = runCatching(block).isFailure
            check(failed) { "accepted a shape that must be refused: $what" }
        }
        refuses("empty header text") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "") }
        refuses("256-character text") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "a".repeat(256)) }
        refuses("trailing bare &") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "&Lpage &") }
        refuses("text with no section marker") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "plain text") }
        refuses("unknown code &Q") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "&Qtext") }
        refuses("&K colour") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "&KFF0000text") }
        refuses("&G picture") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "&G") }
        refuses("&Z theme colour") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "&Z&\"Calibri\"") }
        refuses("unclosed &\"font,style\"") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "&\"Arial,Italic") }
        refuses("duplicate &L") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "&La&Lb") }
        refuses("&R before &L") { header(XlsxHeaderFooter.Slot.ODD_HEADER, "&Rright&Lleft") }
        refuses("duplicate slot") {
            XlsxHeaderFooter.Spec(
                listOf(header(XlsxHeaderFooter.Slot.ODD_HEADER, "&Ca"), header(XlsxHeaderFooter.Slot.ODD_HEADER, "&Cb")),
            )
        }
        refuses("differentOddEven with no even slot") { GRID_HEADER_FOOTER.copy(texts = GRID_HEADER_FOOTER.texts - GRID_HEADER_FOOTER.texts[2]) }
        refuses("differentFirst with no first slot") { GRID_HEADER_FOOTER.copy(differentFirst = true) }
        refuses("empty spec") { XlsxHeaderFooter.Spec() }
        refuses("break id 0") { XlsxBreaks.Brk(id = 0) }
        refuses("break id at the last row") { XlsxBreaks.Brk(id = XlsxBreaks.MAX_ROW) }
        refuses("min > max") { XlsxBreaks.Brk(id = 4, min = 9, max = 2) }
        refuses("row-break span past the column grid") {
            XlsxBreaks.Spec(rows = listOf(XlsxBreaks.Brk(id = 4, min = 0, max = XlsxBreaks.MAX_COLUMN + 1)))
        }
        refuses("column-break span above the last row") {
            XlsxBreaks.Spec(cols = listOf(XlsxBreaks.Brk(id = 4, min = 0, max = XlsxBreaks.MAX_ROW + 1)))
        }
        refuses("negative span") { XlsxBreaks.Brk(id = 4, min = -1) }
        refuses("descending ids") { XlsxBreaks.Spec(rows = listOf(XlsxBreaks.Brk(id = 9), XlsxBreaks.Brk(id = 4))) }
        refuses("duplicate ids") { XlsxBreaks.Spec(rows = listOf(XlsxBreaks.Brk(id = 4), XlsxBreaks.Brk(id = 4))) }
        refuses("column break past the grid") { XlsxBreaks.Spec(cols = listOf(XlsxBreaks.Brk(id = XlsxBreaks.MAX_COLUMN))) }
        refuses("1027 breaks") {
            XlsxBreaks.Spec(rows = (1..(XlsxBreaks.MAX_BREAKS + 1)).map { XlsxBreaks.Brk(id = it) })
        }
        refuses("empty breaks spec") { XlsxBreaks.Spec() }

        // 10. Accepted shapes the model must not over-refuse.
        val fancy = header(XlsxHeaderFooter.Slot.ODD_HEADER, "&L&\"Arial,Italic\"&12Big&R&Bbold&Uunder&Xsup&Ysub&&")
        check(fancy.text.endsWith("&&")) { "a legal code sequence was rejected" }
        check(XlsxBreaks.Spec(rows = (1..XlsxBreaks.MAX_BREAKS).map { XlsxBreaks.Brk(id = it) }).rows.size == 1026) {
            "exactly MAX_BREAKS breaks must be accepted"
        }
        check(XlsxBreaks.Spec(cols = listOf(XlsxBreaks.Brk(id = XlsxBreaks.MAX_COLUMN - 1))).cols.isNotEmpty()) {
            "the last legal column break position was rejected"
        }
        check(XlsxHeaderFooter.Spec(texts = listOf(header(XlsxHeaderFooter.Slot.ODD_HEADER, "&Csmall"))).ordered.size == 1) {
            "a minimal marked section was rejected"
        }

        // 11. A foreign package written in openpyxl's shapes is read, not repaired into ours.
        val foreign = XlsxStyledBook.read(ForeignPackage.build())
        val f = foreign.sheets.single()
        check(f.headerFooter?.texts?.map { it.slot } ==
            listOf(XlsxHeaderFooter.Slot.ODD_HEADER, XlsxHeaderFooter.Slot.ODD_FOOTER, XlsxHeaderFooter.Slot.EVEN_HEADER, XlsxHeaderFooter.Slot.FIRST_HEADER)) {
            "foreign slots were not read in schema order: ${f.headerFooter?.texts?.map { it.slot }}"
        }
        check(f.headerFooter?.differentFirst == true && f.headerFooter?.differentOddEven == false) {
            "foreign flags were not read: ${f.headerFooter}"
        }
        check(f.headerFooter?.scaleWithDoc == false && f.headerFooter?.alignWithMargins == false) {
            "foreign 0-valued flags were not read as false: ${f.headerFooter}"
        }
        check(f.breaks?.rows?.map { it.id } == listOf(12, 30, 99)) {
            "foreign breaks were not sorted and de-duplicated: ${f.breaks?.rows?.map { it.id }}"
        }
        check(f.breaks?.rows?.map { it.manual } == listOf(false, true, false)) {
            "foreign man attributes were misread: ${f.breaks?.rows?.map { it.manual }}"
        }
        check(f.breaks?.cols?.map { it.id } == listOf(5)) { "foreign column breaks were lost: ${f.breaks?.cols}" }
        // A column-break id past the last column is dropped on read instead of throwing out of `Spec`.
        val strayCols = XlsxBreaks.parse(
            dev.fusion.ooxml.BoundedXml.parse(
                ("""<worksheet><colBreaks count="2"><brk id="20000" man="1"/><brk id="4" man="1"/>""" +
                    """</colBreaks></worksheet>""").toByteArray(Charsets.UTF_8),
            ),
        )
        check(strayCols?.cols?.map { it.id } == listOf(4)) {
            "an out-of-grid column break id was not dropped: ${strayCols?.cols?.map { it.id }}"
        }
        check(f.breaks?.rows?.first { it.id == 30 }?.max == 16_383) { "a foreign span was dropped" }

        // 12. Smaller shapes: one record alone, the exact 255-character cap, a quoted font code, spans.
        val onlyBreaks = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                listOf(
                    XlsxStyledBook.StyledSheet(
                        "OnlyBreaks",
                        listOf(cell("A1", "breaks only")),
                        breaks = XlsxBreaks.Spec(cols = listOf(XlsxBreaks.Brk(id = 7, manual = true))),
                    ),
                ),
            ),
        )
        val onlyBreaksSheet = part(onlyBreaks, "xl/worksheets/sheet1.xml")
        check(onlyBreaksSheet.endsWith(
            """</sheetData><colBreaks count="1" manualBreakCount="1"><brk id="7" man="1"/></colBreaks></worksheet>""",
        )) { "a breaks-only sheet did not write the column container directly after sheetData" }
        check(!onlyBreaksSheet.contains("<rowBreaks")) { "a breaks-only sheet invented an empty <rowBreaks>" }

        val onlyHeader = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                listOf(
                    XlsxStyledBook.StyledSheet(
                        "OnlyHeader",
                        listOf(cell("A1", "header only")),
                        headerFooter = XlsxHeaderFooter.Spec(
                            texts = listOf(header(XlsxHeaderFooter.Slot.ODD_HEADER, "&C" + "x".repeat(253))),
                        ),
                    ),
                ),
            ),
        )
        val onlyHeaderSheet = part(onlyHeader, "xl/worksheets/sheet1.xml")
        check(onlyHeaderSheet.contains("<headerFooter><oddHeader>&amp;C" + "x".repeat(253) + "</oddHeader></headerFooter>")) {
            "a 255-character header was not written exactly"
        }
        check(onlyHeaderSheet.endsWith("</headerFooter></worksheet>")) {
            "a header-only sheet wrote something after </headerFooter>"
        }
        check(XlsxStyledBook.read(onlyHeader).sheets[0].headerFooter?.texts?.single()?.text?.length == 255) {
            "a 255-character header did not read back at full length"
        }

        val fontCode = header(XlsxHeaderFooter.Slot.ODD_HEADER, "&C&\"Calibri,Italic\"&14Big")
        val fontBook = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                listOf(
                    XlsxStyledBook.StyledSheet(
                        "FontCode", listOf(cell("A1", "font")),
                        headerFooter = XlsxHeaderFooter.Spec(texts = listOf(fontCode)),
                    ),
                ),
            ),
        )
        check(XlsxStyledBook.read(fontBook).sheets[0].headerFooter?.texts?.single() == fontCode) {
            "a quoted font code did not round-trip: ${XlsxStyledBook.read(fontBook).sheets[0].headerFooter}"
        }
        check(part(fontBook, "xl/worksheets/sheet1.xml").contains("&amp;C&amp;&quot;Calibri,Italic&quot;&amp;14Big")) {
            "a quoted font code was not escaped: " +
                (Regex("<oddHeader>.*?</oddHeader>").find(part(fontBook, "xl/worksheets/sheet1.xml"))?.value)
        }

        // The two records are attributes and children: nothing else in the package may change because of them.
        // Same three sheets, same cells, same print record — only the two R95 records are removed.
        val plainBook = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                listOf(
                    XlsxStyledBook.StyledSheet("Grid", listOf(cell("A1", "header and breaks"), cell("B2", "x")), print = PRINT),
                    XlsxStyledBook.StyledSheet("First", listOf(cell("A1", "title"))),
                    XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "open"))),
                ),
            ),
        )
        for (entry in listOf("xl/workbook.xml", "xl/_rels/workbook.xml.rels", "[Content_Types].xml")) {
            check(part(book, entry) == part(plainBook, entry)) {
                "a header/footer or a break changed $entry"
            }
        }
        check(partOrNull(book, "xl/worksheets/_rels/sheet2.xml.rels") == null) {
            "a header-only sheet grew a relationship part"
        }
        val minOnly = XlsxBreaks.Spec(rows = listOf(XlsxBreaks.Brk(id = 3, manual = true, min = 0)))
        check(XlsxBreaks.toXml(minOnly).contains("""<brk id="3" min="0" man="1"/>""")) {
            "a min-only span was not written: ${XlsxBreaks.toXml(minOnly)}"
        }
        check(XlsxBreaks.Spec(rows = emptyList(), cols = listOf(XlsxBreaks.Brk(id = 2))).manualCount == 1) {
            "the manual count ignored the column axis"
        }

        // 12. Artifacts for the independent readers.
        writeIntent(
            folder,
            listOf("r95.xlsx" to grid, "r95.xlsx" to first, "r95.xlsx" to plain),
        )
        val headRows = File(folder, "headerfooter.tsv").readLines().count { it.isNotBlank() }
        val breakRows = File(folder, "breaks.tsv").readLines().count { it.isNotBlank() }
        check(headRows == 13) { "header/footer intent rows: $headRows (6+6 slots plus one none row)" }
        check(breakRows == 6) { "break intent rows: $breakRows (4 Grid breaks, one First break, one none row)" }
        File(folder, "r95.xlsx").writeBytes(book)
        File(folder, "r95-foreign.xlsx").writeBytes(ForeignPackage.build())

        println(
            "R95_TAIL_OK assertions=$assertions workbooks=5 sheets=6 headerFooter=4 rowBreaks=4 colBreaks=2 " +
                "foreign=1 refused=26",
        )
    }
}
