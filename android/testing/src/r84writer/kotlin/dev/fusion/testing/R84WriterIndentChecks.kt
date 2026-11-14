// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import java.io.File

/**
 * R84 Writer paragraph indentation and keep-with-next/keep-together contracts: `w:ind` (left, right and
 * one signed first-line measurement emitted as `w:firstLine` or `w:hanging`) plus `w:keepNext`/`w:keepLines`,
 * in CT_PPr sequence order, readable back.
 *
 * Host JVM only. The probe writes real `.docx` packages plus `indent.tsv`; the independent reader is
 * tools/check_r84_writer_indent.py, which opens the packages with python-docx (`left_indent`,
 * `right_indent`, `first_line_indent`, `keep_with_next`, `keep_together`) and inspects the raw OPC parts.
 * Nothing here is Word rendering evidence, and no caller sets the new fields yet.
 */
object R84WriterIndentChecks {
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

    private fun paragraph(
        value: String,
        left: Int? = null,
        right: Int? = null,
        firstLine: Int? = null,
        keepWithNext: Boolean? = null,
        keepTogether: Boolean? = null,
        before: Int? = null,
        after: Int? = null,
        alignment: DocxPackage.Align? = null,
    ) = DocxPackage.Block.Paragraph(
        listOf(DocxPackage.Run(value)), alignment, before, after, left, right, firstLine, keepWithNext, keepTogether,
    )

    private fun heading(
        level: Int,
        value: String,
        left: Int? = null,
        right: Int? = null,
        firstLine: Int? = null,
        keepWithNext: Boolean? = null,
        keepTogether: Boolean? = null,
    ) = DocxPackage.Block.Heading(
        level, listOf(DocxPackage.Run(value)), null, null, null, left, right, firstLine, keepWithNext, keepTogether,
    )

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

    private fun write(out: File, name: String, blocks: List<DocxPackage.Block>, layout: DocxPackage.Layout? = null): ByteArray {
        val document = DocxPackage.Document(title = name, blocks = blocks, layout = layout)
        val bytes = DocxPackage.write(document)
        File(out, "$name.docx").writeBytes(bytes)
        val readBack = DocxPackage.read(bytes)
        check(readBack.blocks == blocks) { "$name block round trip: ${readBack.blocks}" }
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
            val left = when (block) {
                is DocxPackage.Block.Heading -> block.indentLeftTwips
                is DocxPackage.Block.Paragraph -> block.indentLeftTwips
                else -> null
            }
            val right = when (block) {
                is DocxPackage.Block.Heading -> block.indentRightTwips
                is DocxPackage.Block.Paragraph -> block.indentRightTwips
                else -> null
            }
            val firstLine = when (block) {
                is DocxPackage.Block.Heading -> block.firstLineTwips
                is DocxPackage.Block.Paragraph -> block.firstLineTwips
                else -> null
            }
            val keepNext = when (block) {
                is DocxPackage.Block.Heading -> block.keepWithNext
                is DocxPackage.Block.Paragraph -> block.keepWithNext
                else -> null
            }
            val keepTogether = when (block) {
                is DocxPackage.Block.Heading -> block.keepTogether
                is DocxPackage.Block.Paragraph -> block.keepTogether
                else -> null
            }
            rows.add(
                listOf(
                    name, index, kind, left ?: "-", right ?: "-", firstLine ?: "-",
                    keepNext?.toString() ?: "-", keepTogether?.toString() ?: "-",
                ).joinToString("\t"),
            )
            index++
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: nothing indented, nothing kept ------------------------------------
        val plain = listOf(DocxPackage.Block.Heading(1, listOf(DocxPackage.Run("Plain heading"))),
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("Plain body."))))
        val plainBytes = write(out, "default", plain)
        check("w:ind" !in body(plainBytes)) { "a document without indents must not emit w:ind" }
        check("w:keepNext" !in body(plainBytes) && "w:keepLines" !in body(plainBytes)) {
            "a document without keep flags must not emit them"
        }
        check(body(plainBytes).startsWith("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>")) {
            "the default heading keeps its pre-R84 prefix: ${body(plainBytes)}"
        }
        check(body(plainBytes).contains("<w:p><w:r><w:t xml:space=\"preserve\">Plain body.</w:t></w:r></w:p>")) {
            "the default paragraph keeps its pre-R84 bytes"
        }
        record(rows, "default", plain)

        // --- one-sided indents ---------------------------------------------------------------------
        val leftOnly = listOf(paragraph("left indent", left = 720))
        check(body(write(out, "indent-left", leftOnly)).contains("<w:pPr><w:ind w:left=\"720\"/></w:pPr>")) {
            "a left indent is emitted as w:left: ${body(write(out, "indent-left", leftOnly))}"
        }
        record(rows, "indent-left", leftOnly)

        val rightOnly = listOf(paragraph("right indent", right = 360))
        check(body(write(out, "indent-right", rightOnly)).contains("<w:pPr><w:ind w:right=\"360\"/></w:pPr>")) {
            "a right indent is emitted as w:right"
        }
        record(rows, "indent-right", rightOnly)

        // --- the signed first-line measurement: negative means a hanging indent --------------------
        val hanging = listOf(paragraph("hanging", firstLine = -240))
        val hangingBody = body(write(out, "hanging", hanging))
        check(hangingBody.contains("<w:ind w:hanging=\"240\"/>")) { "a negative first line is w:hanging: $hangingBody" }
        check("w:firstLine" !in hangingBody) { "w:hanging must not be accompanied by w:firstLine" }
        record(rows, "hanging", hanging)

        val firstLine = listOf(paragraph("first line", firstLine = 480))
        val firstLineBody = body(write(out, "first-line", firstLine))
        check(firstLineBody.contains("<w:ind w:firstLine=\"480\"/>")) { "a positive first line is w:firstLine" }
        check("w:hanging" !in firstLineBody) { "w:firstLine must not be accompanied by w:hanging" }
        record(rows, "first-line", firstLine)

        // --- all three measurements plus both keep flags -------------------------------------------
        val combined = listOf(
            paragraph("combined", left = 720, right = 360, firstLine = -240, keepWithNext = true, keepTogether = true),
        )
        val combinedBytes = write(out, "combined", combined)
        val combinedBody = body(combinedBytes)
        check(combinedBody.contains(
            "<w:pPr><w:keepNext/><w:keepLines/><w:ind w:left=\"720\" w:right=\"360\" w:hanging=\"240\"/></w:pPr>",
        )) { "CT_PPr order is keepNext, keepLines, ind: $combinedBody" }
        record(rows, "combined", combined)

        // --- keep flags on a heading, and their interaction with spacing and alignment --------------
        val keptHeading = listOf(heading(2, "Kept heading", keepWithNext = true, keepTogether = true))
        val keptBody = body(write(out, "heading-keep", keptHeading))
        check(keptBody.startsWith(
            "<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/><w:keepNext/><w:keepLines/></w:pPr>",
        )) { "keep flags follow w:pStyle: $keptBody" }
        record(rows, "heading-keep", keptHeading)

        val full = listOf(
            DocxPackage.Block.Paragraph(
                listOf(DocxPackage.Run("full")), DocxPackage.Align.CENTER, 120, 240, 720, 360, -240, true, true,
            ),
        )
        val fullBody = body(write(out, "full", full))
        check(fullBody.contains(
            "<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before=\"120\" w:after=\"240\"/>" +
                "<w:ind w:left=\"720\" w:right=\"360\" w:hanging=\"240\"/><w:jc w:val=\"center\"/></w:pPr>",
        )) { "the full CT_PPr sequence is keepNext, keepLines, spacing, ind, jc: $fullBody" }
        record(rows, "full", full)

        // --- explicit off is written as w:val="0", distinct from inheriting the style hierarchy ------
        val off = listOf(
            paragraph("off", keepWithNext = false),
            heading(2, "Off heading", keepTogether = false),
        )
        val offBytes = write(out, "keep-off", off)
        val offBody = body(offBytes)
        check(offBody.contains("<w:keepNext w:val=\"0\"/>")) { "an explicit off is emitted as w:val=\"0\": $offBody" }
        check(offBody.contains("<w:keepLines w:val=\"0\"/>")) { "an explicit off is emitted as w:val=\"0\": $offBody" }
        check("<w:keepNext/>" !in offBody) { "an explicit off must not be written as a bare element" }
        record(rows, "keep-off", off)

        // --- a foreign file carrying both firstLine and hanging: ECMA-376 makes firstLine win --------
        val both = rezip(combinedBytes, "word/document.xml") {
            it.replaceFirst("w:hanging=\"240\"", "w:hanging=\"240\" w:firstLine=\"120\"")
        }
        check("w:firstLine=\"120\"" in part(both, "word/document.xml")) { "the patched part carries both" }
        val bothRead = DocxPackage.read(both).blocks.single() as DocxPackage.Block.Paragraph
        check(bothRead.firstLineTwips == 120) {
            "w:firstLine wins over w:hanging, as ECMA-376 requires: ${bothRead.firstLineTwips}"
        }

        // --- explicit zeros are emitted, not elided ------------------------------------------------
        val zeros = listOf(paragraph("zeros", left = 0, right = 0, firstLine = 0))
        val zerosBody = body(write(out, "zero", zeros))
        check(zerosBody.contains("<w:ind w:left=\"0\" w:right=\"0\" w:firstLine=\"0\"/>")) {
            "explicit zeros are emitted: $zerosBody"
        }
        record(rows, "zero", zeros)

        // --- scope: list items, quotes, code blocks and table cells stay untouched ------------------
        val mixed = listOf(
            heading(2, "Mixed", left = 360, keepWithNext = true),
            paragraph("indented body", left = 720, firstLine = 240, alignment = DocxPackage.Align.RIGHT),
            DocxPackage.Block.ListItem(false, 0, listOf(DocxPackage.Run("bullet"))),
            DocxPackage.Block.BlockQuote(listOf(DocxPackage.Run("quoted"))),
            DocxPackage.Block.CodeBlock(listOf("val x = 1")),
            DocxPackage.Block.Table(listOf(listOf("A", "B")), columnWidthsTwips = listOf(2_400, 2_400)),
        )
        val mixedBytes = write(out, "mixed", mixed)
        val mixedBody = body(mixedBytes)
        check(mixedBody.split("w:ind").size == 3) {
            "exactly two w:ind elements in a mixed document (found ${mixedBody.split("w:ind").size - 1})"
        }
        check(mixedBody.split("<w:keepNext/>").size == 2) { "exactly one w:keepNext in a mixed document" }
        check(!mixedBody.substringAfter("<w:pStyle w:val=\"ListParagraph\"/>").substringBefore("</w:pPr>").contains("w:ind")) {
            "a list item must not be indented by this feature"
        }
        check(!mixedBody.substringAfter("<w:pStyle w:val=\"Quote\"/>").substringBefore("</w:pPr>").contains("w:ind")) {
            "a quote must not be indented by this feature"
        }
        check(!mixedBody.substringAfter("<w:tbl>").contains("w:ind")) { "a table cell must not be indented by this feature" }
        check("<w:tblPr><w:tblW w:w=\"4800\"" in mixedBody) { "the sized table keeps its geometry" }
        record(rows, "mixed", mixed)

        // --- indentation beside a section layout, header/footer and a hyperlink ---------------------
        val layout = DocxPackage.Layout(
            pageSetup = DocxPackage.PageSetup.a4(), header = "R84 header", footer = "R84 footer",
        )
        val layoutDoc = listOf(
            heading(1, "Layout heading", left = 720, firstLine = -240, keepWithNext = true),
            DocxPackage.Block.Paragraph(
                listOf(DocxPackage.Run("linked", href = "https://example.org/r84")), null, null, null, 240, null, 0,
            ),
        )
        val layoutBytes = write(out, "layout-indent", layoutDoc, layout)
        check(DocxPackage.readLayout(layoutBytes) == layout) { "indentation does not disturb the section layout" }
        check("w:hyperlink" in text(layoutBytes)) { "the hyperlink survives" }
        record(rows, "layout-indent", layoutDoc)

        // --- import leniency: ST_OnOff says w:val="0" is off, and ISO strict spells w:start for w:left ---
        val keptOn = rezip(combinedBytes, "word/document.xml") {
            it.replaceFirst("<w:keepNext/>", "<w:keepNext w:val=\"0\"/>")
        }
        check("w:keepNext w:val=\"0\"" in part(keptOn, "word/document.xml")) {
            "the patched part really carries w:keepNext w:val=\"0\""
        }
        val keptOnRead = DocxPackage.read(keptOn).blocks.single() as DocxPackage.Block.Paragraph
        check(keptOnRead.keepWithNext == false) { "w:keepNext w:val=\"0\" imports as an explicit off" }
        check(keptOnRead.keepTogether == true) { "the sibling w:keepLines still imports as on" }

        val aliased = rezip(combinedBytes, "word/document.xml") {
            it.replaceFirst("w:left=\"720\"", "w:start=\"720\"")
        }
        val aliasedRead = DocxPackage.read(aliased).blocks.single() as DocxPackage.Block.Paragraph
        check(aliasedRead.indentLeftTwips == 720) { "the ISO-strict w:start alias imports as w:left" }

        // --- boundaries and refusals ---------------------------------------------------------------
        check(paragraph("max", left = 31_680, right = -31_680, firstLine = -31_680).firstLineTwips == -31_680) {
            "the signed ceiling is accepted on both sides"
        }
        fails("left above the ceiling") { paragraph("bad", left = 31_681) }
        fails("left below the floor") { paragraph("bad", left = -31_681) }
        fails("right above the ceiling") { paragraph("bad", right = 31_681) }
        fails("first line above the ceiling") { paragraph("bad", firstLine = 31_681) }
        fails("first line below the floor") { paragraph("bad", firstLine = -31_681) }
        fails("heading indent above the ceiling") { heading(1, "bad", left = 31_681) }
        check(DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x"))) ==
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x")), null, null, null, null, null, null, null, null)) {
            "the default arguments keep the plain Paragraph shape"
        }

        File(out, "indent.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R84_WRITER_INDENT_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size}")
    }
}
