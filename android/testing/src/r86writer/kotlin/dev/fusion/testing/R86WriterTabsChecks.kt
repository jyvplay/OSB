// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import java.io.File

/**
 * R86 Writer paragraph tab-stop, page-break-before and widow-control contracts: `w:tabs` (each `w:tab`
 * with an explicit `w:val`, a `w:pos` in 0..31680 twips and an optional `w:leader`), `w:pageBreakBefore`
 * and `w:widowControl` as tri-state CT_OnOff children, all in CT_PPr sequence order, readable back.
 *
 * Host JVM only. The probe writes real `.docx` packages plus `tabs.tsv`; the independent reader is
 * tools/check_r86_writer_tabs.py, which opens the packages with python-docx (`tab_stops`,
 * `page_break_before`, `widow_control`) and inspects the raw OPC parts. Nothing here is Word rendering
 * or pagination evidence, and no caller sets the new fields yet.
 */
object R86WriterTabsChecks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun fails(message: String, block: () -> Unit) {
        assertions++
        val failure = runCatching(block).exceptionOrNull()
        require(failure != null) { "expected a refusal for $message" }
        require(failure is IllegalArgumentException) { "refusal for $message must be IllegalArgumentException, was $failure" }
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun body(bytes: ByteArray): String =
        text(bytes).substringAfter("<w:body>").substringBefore("<w:sectPr")

    /** Reads one OPC part back out of a package, compressed or not. */
    private fun part(bytes: ByteArray, entry: String): String {
        var found: String? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            while (found == null) {
                val current = input.nextEntry ?: break
                val payload = input.readBytes()
                if (current.name == entry) found = text(payload)
            }
        }
        return found ?: error("missing OPC part $entry")
    }

    /** Rewrites one OPC part of a real package, so import leniency is tested on bytes, not on a mock. */
    private fun rezip(bytes: ByteArray, entry: String, transform: (String) -> String): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            java.util.zip.ZipOutputStream(buffer).use { output ->
                while (true) {
                    val current = input.nextEntry ?: break
                    val payload = input.readBytes()
                    val patched =
                        if (current.name == entry) transform(text(payload)).toByteArray(Charsets.ISO_8859_1)
                        else payload
                    output.putNextEntry(java.util.zip.ZipEntry(current.name))
                    output.write(patched)
                    output.closeEntry()
                }
            }
        }
        return buffer.toByteArray()
    }

    private fun stop(
        position: Int,
        align: DocxPackage.TabAlign = DocxPackage.TabAlign.LEFT,
        leader: DocxPackage.TabLeader? = null,
    ) = DocxPackage.TabStop(position, align, leader)

    private fun paragraph(
        value: String,
        pageBreakBefore: Boolean? = null,
        widowControl: Boolean? = null,
        tabs: List<DocxPackage.TabStop> = emptyList(),
        keepWithNext: Boolean? = null,
        keepTogether: Boolean? = null,
        before: Int? = null,
        after: Int? = null,
        left: Int? = null,
        right: Int? = null,
        firstLine: Int? = null,
        alignment: DocxPackage.Align? = null,
    ) = DocxPackage.Block.Paragraph(
        runs = listOf(DocxPackage.Run(value)),
        alignment = alignment,
        spaceBeforeTwips = before,
        spaceAfterTwips = after,
        indentLeftTwips = left,
        indentRightTwips = right,
        firstLineTwips = firstLine,
        keepWithNext = keepWithNext,
        keepTogether = keepTogether,
        pageBreakBefore = pageBreakBefore,
        widowControl = widowControl,
        tabStops = tabs,
    )

    private fun heading(
        level: Int,
        value: String,
        pageBreakBefore: Boolean? = null,
        widowControl: Boolean? = null,
        tabs: List<DocxPackage.TabStop> = emptyList(),
    ) = DocxPackage.Block.Heading(
        level = level,
        runs = listOf(DocxPackage.Run(value)),
        pageBreakBefore = pageBreakBefore,
        widowControl = widowControl,
        tabStops = tabs,
    )

    private fun tabIntent(stops: List<DocxPackage.TabStop>): String =
        if (stops.isEmpty()) "-" else stops.joinToString(";") { s ->
            "${s.positionTwips}:${s.align.token}:${s.leader?.token ?: "none"}"
        }

    private fun write(
        out: File,
        name: String,
        blocks: List<DocxPackage.Block>,
        layout: DocxPackage.Layout? = null,
    ): ByteArray {
        val document = DocxPackage.Document(title = name, blocks = blocks, layout = layout)
        val bytes = DocxPackage.write(document)
        File(out, "$name.docx").writeBytes(bytes)
        val readBack = DocxPackage.read(bytes)
        check(readBack.blocks == blocks) { "$name block round trip: ${readBack.blocks}" }
        if (layout != null) check(DocxPackage.readLayout(bytes) == layout) { "$name layout round trip" }
        return bytes
    }

    /** One intent row per body `w:p`, in body order (the order python-docx reports paragraphs in). */
    private fun record(rows: MutableList<String>, name: String, blocks: List<DocxPackage.Block>) {
        var index = 0
        for (block in blocks) {
            if (block is DocxPackage.Block.Table) continue
            val kind = when (block) {
                is DocxPackage.Block.Heading -> "heading"
                is DocxPackage.Block.Paragraph -> "paragraph"
                is DocxPackage.Block.ListItem -> "list"
                is DocxPackage.Block.BlockQuote -> "quote"
                is DocxPackage.Block.CodeBlock -> "code"
                else -> "other"
            }
            val breakBefore = when (block) {
                is DocxPackage.Block.Heading -> block.pageBreakBefore
                is DocxPackage.Block.Paragraph -> block.pageBreakBefore
                else -> null
            }
            val widow = when (block) {
                is DocxPackage.Block.Heading -> block.widowControl
                is DocxPackage.Block.Paragraph -> block.widowControl
                else -> null
            }
            val tabs = when (block) {
                is DocxPackage.Block.Heading -> block.tabStops
                is DocxPackage.Block.Paragraph -> block.tabStops
                else -> emptyList()
            }
            rows.add(
                listOf(
                    name, index, kind, breakBefore?.toString() ?: "-", widow?.toString() ?: "-",
                    tabIntent(tabs),
                ).joinToString("\t"),
            )
            index++
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: nothing declared, so no new element appears -------------------------
        val plain = listOf(
            DocxPackage.Block.Heading(1, listOf(DocxPackage.Run("Plain heading"))),
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("Plain body."))),
        )
        val plainBytes = write(out, "default", plain)
        check("w:tabs" !in body(plainBytes)) { "a document without tab stops must not emit w:tabs" }
        check("w:pageBreakBefore" !in body(plainBytes) && "w:widowControl" !in body(plainBytes)) {
            "a document without the new flags must not emit them"
        }
        check(body(plainBytes).startsWith("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>")) {
            "the default heading keeps its pre-R86 prefix: ${body(plainBytes)}"
        }
        check(body(plainBytes).contains("<w:p><w:r><w:t xml:space=\"preserve\">Plain body.</w:t></w:r></w:p>")) {
            "the default paragraph keeps its pre-R86 bytes"
        }
        record(rows, "default", plain)

        // --- one stop: the schema default alignment must still be spelled out -----------------------
        val basic = listOf(paragraph("basic", tabs = listOf(stop(720))))
        val basicBody = body(write(out, "tabs-basic", basic))
        check(basicBody.contains("<w:tabs><w:tab w:val=\"left\" w:pos=\"720\"/></w:tabs>")) {
            "one left stop is written with an explicit w:val: $basicBody"
        }
        record(rows, "tabs-basic", basic)

        // --- all six ST_TabTblAlign tokens ----------------------------------------------------------
        val aligns = listOf(
            stop(720, DocxPackage.TabAlign.LEFT),
            stop(1_440, DocxPackage.TabAlign.CENTER),
            stop(2_160, DocxPackage.TabAlign.RIGHT),
            stop(2_880, DocxPackage.TabAlign.DECIMAL),
            stop(3_600, DocxPackage.TabAlign.BAR),
            stop(4_320, DocxPackage.TabAlign.CLEAR),
        )
        val alignDoc = listOf(paragraph("aligns", tabs = aligns))
        val alignBody = body(write(out, "tabs-aligns", alignDoc))
        val alignExpected = "<w:tabs>" +
            "<w:tab w:val=\"left\" w:pos=\"720\"/>" +
            "<w:tab w:val=\"center\" w:pos=\"1440\"/>" +
            "<w:tab w:val=\"right\" w:pos=\"2160\"/>" +
            "<w:tab w:val=\"decimal\" w:pos=\"2880\"/>" +
            "<w:tab w:val=\"bar\" w:pos=\"3600\"/>" +
            "<w:tab w:val=\"clear\" w:pos=\"4320\"/></w:tabs>"
        check(alignExpected in alignBody) { "every alignment token is emitted in order: $alignBody" }
        record(rows, "tabs-aligns", alignDoc)

        // --- every leader token --------------------------------------------------------------------
        val leaders = listOf(
            stop(720, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.DOT),
            stop(1_440, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.HYPHEN),
            stop(2_160, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.UNDERSCORE),
            stop(2_880, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.HEAVY),
            stop(3_600, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.MIDDLE_DOT),
        )
        val leaderDoc = listOf(paragraph("leaders", tabs = leaders))
        val leaderBody = body(write(out, "tabs-leaders", leaderDoc))
        val leaderExpected = "<w:tabs>" +
            "<w:tab w:val=\"right\" w:pos=\"720\" w:leader=\"dot\"/>" +
            "<w:tab w:val=\"right\" w:pos=\"1440\" w:leader=\"hyphen\"/>" +
            "<w:tab w:val=\"right\" w:pos=\"2160\" w:leader=\"underscore\"/>" +
            "<w:tab w:val=\"right\" w:pos=\"2880\" w:leader=\"heavy\"/>" +
            "<w:tab w:val=\"right\" w:pos=\"3600\" w:leader=\"middleDot\"/></w:tabs>"
        check(leaderExpected in leaderBody) { "every leader token is emitted in order: $leaderBody" }
        record(rows, "tabs-leaders", leaderDoc)

        // --- page break before: on, off and inherited ----------------------------------------------
        val breaks = listOf(
            paragraph("break on", pageBreakBefore = true),
            paragraph("break off", pageBreakBefore = false),
            paragraph("break inherited"),
        )
        val breakBody = body(write(out, "break-before", breaks))
        check("<w:pageBreakBefore/>" in breakBody) { "an explicit on is a bare w:pageBreakBefore: $breakBody" }
        check("<w:pageBreakBefore w:val=\"0\"/>" in breakBody) {
            "an explicit off is w:val=\"0\", not an omission"
        }
        check(breakBody.split("w:pageBreakBefore").size - 1 == 2) {
            "the inherited paragraph writes no w:pageBreakBefore at all"
        }
        record(rows, "break-before", breaks)

        // --- widow control: on, off and inherited --------------------------------------------------
        val widows = listOf(
            paragraph("widow on", widowControl = true),
            paragraph("widow off", widowControl = false),
            paragraph("widow inherited"),
        )
        val widowBody = body(write(out, "widow", widows))
        check("<w:widowControl/>" in widowBody) { "an explicit on is a bare w:widowControl: $widowBody" }
        check("<w:widowControl w:val=\"0\"/>" in widowBody) { "an explicit off is w:val=\"0\"" }
        check(widowBody.split("w:widowControl").size - 1 == 2) {
            "the inherited paragraph writes no w:widowControl at all"
        }
        record(rows, "widow", widows)

        // --- the full CT_PPr sequence with every child this writer can emit ------------------------
        val ordered = listOf(
            paragraph(
                "ordered",
                pageBreakBefore = true,
                widowControl = false,
                tabs = listOf(stop(1_440, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.DOT)),
                keepWithNext = true,
                keepTogether = true,
                before = 120,
                after = 240,
                left = 720,
                alignment = DocxPackage.Align.CENTER,
            ),
        )
        val orderedBody = body(write(out, "ordered", ordered))
        val orderedExpected = "<w:pPr><w:keepNext/><w:keepLines/><w:pageBreakBefore/>" +
            "<w:widowControl w:val=\"0\"/>" +
            "<w:tabs><w:tab w:val=\"right\" w:pos=\"1440\" w:leader=\"dot\"/></w:tabs>" +
            "<w:spacing w:before=\"120\" w:after=\"240\"/><w:ind w:left=\"720\"/>" +
            "<w:jc w:val=\"center\"/></w:pPr>"
        check(orderedExpected in orderedBody) { "the eight children are in CT_PPr order: $orderedBody" }
        record(rows, "ordered", ordered)

        // --- both ends of the accepted position window --------------------------------------------
        val boundary = listOf(paragraph("boundary", tabs = listOf(stop(0), stop(31_680, DocxPackage.TabAlign.RIGHT))))
        val boundaryBody = body(write(out, "boundary", boundary))
        check("<w:tab w:val=\"left\" w:pos=\"0\"/>" in boundaryBody) { "a stop at the left margin is legal" }
        check("<w:tab w:val=\"right\" w:pos=\"31680\"/>" in boundaryBody) { "the 22-inch ceiling is legal" }
        record(rows, "boundary", boundary)

        // --- headings carry the same three properties ---------------------------------------------
        val headingDoc = listOf(
            heading(
                2, "Heading", pageBreakBefore = true, widowControl = true,
                tabs = listOf(stop(2_880, DocxPackage.TabAlign.DECIMAL, DocxPackage.TabLeader.HEAVY)),
            ),
        )
        val headingBody = body(write(out, "heading-tabs", headingDoc))
        check(
            "<w:pPr><w:pStyle w:val=\"Heading2\"/><w:pageBreakBefore/><w:widowControl/>" +
                "<w:tabs><w:tab w:val=\"decimal\" w:pos=\"2880\" w:leader=\"heavy\"/></w:tabs></w:pPr>" in headingBody,
        ) { "a heading carries pageBreakBefore, widowControl and tabs after its pStyle: $headingBody" }
        record(rows, "heading-tabs", headingDoc)

        // --- scope: list items, quotes, code blocks and table cells stay untouched -----------------
        val mixed = listOf(
            heading(2, "Mixed", pageBreakBefore = true, tabs = listOf(stop(1_440))),
            paragraph("body", widowControl = false, tabs = listOf(stop(720, DocxPackage.TabAlign.CENTER))),
            DocxPackage.Block.ListItem(false, 0, listOf(DocxPackage.Run("bullet"))),
            DocxPackage.Block.BlockQuote(listOf(DocxPackage.Run("quoted"))),
            DocxPackage.Block.CodeBlock(listOf("val x = 1")),
            DocxPackage.Block.Table(listOf(listOf("A", "B")), columnWidthsTwips = listOf(2_400, 2_400)),
        )
        val mixedBytes = write(out, "mixed", mixed)
        val mixedBody = body(mixedBytes)
        check(mixedBody.split("<w:tabs>").size - 1 == 2) { "exactly two w:tabs elements in a mixed document" }
        check(mixedBody.split("w:pageBreakBefore").size - 1 == 1) { "exactly one w:pageBreakBefore" }
        check(!mixedBody.substringAfter("<w:pStyle w:val=\"ListParagraph\"/>").substringBefore("</w:pPr>")
            .contains("w:tabs")) { "a list item must not carry tab stops" }
        check(!mixedBody.substringAfter("<w:pStyle w:val=\"Quote\"/>").substringBefore("</w:pPr>")
            .contains("w:tabs")) { "a quote must not carry tab stops" }
        check(!mixedBody.substringAfter("<w:tbl>").contains("w:tab")) { "a table cell must not carry tab stops" }
        check("<w:tblPr><w:tblW w:w=\"4800\"" in mixedBody) { "the sized table keeps its geometry" }
        record(rows, "mixed", mixed)

        // --- tab stops beside a section layout, header/footer and a hyperlink ----------------------
        val layout = DocxPackage.Layout(
            pageSetup = DocxPackage.PageSetup.a4(), header = "R86 header", footer = "R86 footer",
        )
        val layoutDoc = listOf(
            heading(1, "Layout heading", pageBreakBefore = true, tabs = listOf(stop(1_440, DocxPackage.TabAlign.RIGHT))),
            DocxPackage.Block.Paragraph(
                runs = listOf(DocxPackage.Run("linked", href = "https://example.org/r86")),
                widowControl = false,
            ),
        )
        val layoutBytes = write(out, "layout-tabs", layoutDoc, layout)
        check("w:hyperlink" in text(layoutBytes)) { "the hyperlink survives" }
        check(DocxPackage.readLayout(layoutBytes) == layout) { "tab stops do not disturb the section layout" }
        record(rows, "layout-tabs", layoutDoc)

        // --- import: ST_OnOff spellings and lenient tab handling on a real package ----------------
        val orderedBytes = DocxPackage.write(DocxPackage.Document(title = "ordered", blocks = ordered))
        val offRead = DocxPackage.read(
            rezip(orderedBytes, "word/document.xml") {
                it.replaceFirst("<w:pageBreakBefore/>", "<w:pageBreakBefore w:val=\"off\"/>")
            },
        ).blocks.single() as DocxPackage.Block.Paragraph
        check(offRead.pageBreakBefore == false) { "w:pageBreakBefore w:val=\"off\" imports as an explicit off" }
        check(offRead.widowControl == false) { "the sibling w:widowControl still imports as an explicit off" }
        check(offRead.tabStops.single().leader == DocxPackage.TabLeader.DOT) { "the leader survives the patch" }

        val bareWidow = DocxPackage.read(
            rezip(orderedBytes, "word/document.xml") {
                it.replaceFirst("<w:widowControl w:val=\"0\"/>", "<w:widowControl/>")
            },
        ).blocks.single() as DocxPackage.Block.Paragraph
        check(bareWidow.widowControl == true) { "a bare w:widowControl imports as on" }

        // A foreign file may put w:tabs before w:keepLines; the reader must still see the stops.
        val movedTabs = DocxPackage.read(
            rezip(orderedBytes, "word/document.xml") {
                val tabs = "<w:tabs>" + it.substringAfter("<w:tabs>").substringBefore("</w:tabs>") + "</w:tabs>"
                val without = it.replaceFirst(tabs, "")
                without.replaceFirst("<w:keepNext/>", tabs + "<w:keepNext/>")
            },
        ).blocks.single() as DocxPackage.Block.Paragraph
        check(movedTabs.tabStops.single().positionTwips == 1_440) {
            "an out-of-order w:tabs is still imported: ${movedTabs.tabStops}"
        }

        val alignBytes = DocxPackage.write(DocxPackage.Document(title = "aligns", blocks = alignDoc))
        val descending = DocxPackage.read(
            rezip(alignBytes, "word/document.xml") {
                it.replaceFirst("w:pos=\"720\"", "w:pos=\"5000\"")
            },
        ).blocks.single() as DocxPackage.Block.Paragraph
        check(descending.tabStops.map { it.positionTwips } == descending.tabStops.map { it.positionTwips }.sorted()) {
            "a descending file is normalised to ascending order: ${descending.tabStops.map { it.positionTwips }}"
        }
        check(descending.tabStops.size == 6) { "normalising keeps every distinct stop: ${descending.tabStops.size}" }

        val duplicated = DocxPackage.read(
            rezip(alignBytes, "word/document.xml") {
                it.replaceFirst("w:pos=\"1440\"", "w:pos=\"720\"")
            },
        ).blocks.single() as DocxPackage.Block.Paragraph
        check(duplicated.tabStops.size == 5) { "a duplicate position collapses to one stop: ${duplicated.tabStops.size}" }

        val outOfWindow = DocxPackage.read(
            rezip(alignBytes, "word/document.xml") {
                it.replaceFirst("w:pos=\"4320\"", "w:pos=\"40000\"")
            },
        ).blocks.single() as DocxPackage.Block.Paragraph
        check(outOfWindow.tabStops.none { it.positionTwips > 31_680 }) {
            "a stop outside the 22-inch window is dropped on import: ${outOfWindow.tabStops}"
        }

        val noPosition = DocxPackage.read(
            rezip(alignBytes, "word/document.xml") {
                it.replaceFirst("<w:tab w:val=\"left\" w:pos=\"720\"/>", "<w:tab w:val=\"left\"/>")
            },
        ).blocks.single() as DocxPackage.Block.Paragraph
        check(noPosition.tabStops.size == 5) { "a w:tab without w:pos is skipped: ${noPosition.tabStops.size}" }

        val unknownToken = DocxPackage.read(
            rezip(alignBytes, "word/document.xml") {
                it.replaceFirst("w:val=\"clear\"", "w:val=\"num\"")
            },
        ).blocks.single() as DocxPackage.Block.Paragraph
        check(unknownToken.tabStops.last().align == DocxPackage.TabAlign.LEFT) {
            "an alignment token this writer never emits falls back to left: ${unknownToken.tabStops.last()}"
        }

        // --- boundaries and refusals ---------------------------------------------------------------
        val sixtyFour = (0 until 64).map { stop(it * 480) }
        check(DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x")), tabStops = sixtyFour).tabStops.size == 64) {
            "64 stops are accepted"
        }
        fails("65 stops") {
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x")), tabStops = (0 until 65).map { stop(it * 480) })
        }
        fails("negative position") { paragraph("bad", tabs = listOf(stop(-1))) }
        fails("position above the ceiling") { paragraph("bad", tabs = listOf(stop(31_681))) }
        fails("duplicate positions") { paragraph("bad", tabs = listOf(stop(720), stop(720))) }
        fails("descending positions") { paragraph("bad", tabs = listOf(stop(1_440), stop(720))) }
        fails("heading with a bad stop") { heading(1, "bad", tabs = listOf(stop(31_681))) }
        check(
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x"))) ==
                DocxPackage.Block.Paragraph(
                    listOf(DocxPackage.Run("x")), null, null, null, null, null, null, null, null, null, null,
                    emptyList(),
                ),
        ) { "the default arguments keep the plain Paragraph shape" }

        File(out, "tabs.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R86_WRITER_TABS_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size}")
    }
}
