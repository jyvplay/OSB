// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import java.io.File

/**
 * R83 Writer paragraph-spacing contracts: `Block.Heading`/`Block.Paragraph` `spaceBeforeTwips` and
 * `spaceAfterTwips` emit `w:spacing` inside `w:pPr` between `w:numPr` and `w:jc`, and read back.
 *
 * Host JVM only. The probe writes real `.docx` packages plus `spacing.tsv`; the independent reader is
 * tools/check_r83_writer_spacing.py, which opens the packages with python-docx
 * (`paragraph_format.space_before/after`, which are real `Length` values) and inspects the raw OPC
 * parts. Nothing here is Word rendering evidence, and no caller sets spacing yet.
 */
object R83WriterSpacingChecks {
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

    private fun paragraph(value: String, before: Int? = null, after: Int? = null, alignment: DocxPackage.Align? = null) =
        DocxPackage.Block.Paragraph(listOf(DocxPackage.Run(value)), alignment, before, after)

    private fun heading(level: Int, value: String, before: Int? = null, after: Int? = null) =
        DocxPackage.Block.Heading(level, listOf(DocxPackage.Run(value)), null, before, after)

    private fun write(out: File, name: String, blocks: List<DocxPackage.Block>, layout: DocxPackage.Layout? = null): ByteArray {
        val document = DocxPackage.Document(title = name, blocks = blocks, layout = layout)
        val bytes = DocxPackage.write(document)
        File(out, "$name.docx").writeBytes(bytes)
        check(DocxPackage.read(bytes).blocks == blocks) { "$name block round trip: ${DocxPackage.read(bytes).blocks}" }
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
            val before = when (block) {
                is DocxPackage.Block.Heading -> block.spaceBeforeTwips
                is DocxPackage.Block.Paragraph -> block.spaceBeforeTwips
                else -> null
            }
            val after = when (block) {
                is DocxPackage.Block.Heading -> block.spaceAfterTwips
                is DocxPackage.Block.Paragraph -> block.spaceAfterTwips
                else -> null
            }
            rows.add(listOf(name, index, kind, before ?: "-", after ?: "-").joinToString("\t"))
            index++
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: no spacing means no w:spacing -----------------------------------
        val plain = listOf(heading(1, "Default heading"), paragraph("Default body."))
        val plainBytes = write(out, "default", plain)
        check("w:spacing" !in body(plainBytes)) { "a document without spacing must not emit w:spacing" }
        check(body(plainBytes).startsWith("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>")) {
            "the default heading keeps its pre-R83 prefix: ${body(plainBytes)}"
        }
        check(body(plainBytes).contains("<w:p><w:r><w:t xml:space=\"preserve\">Default body.</w:t></w:r></w:p>")) {
            "the default paragraph keeps its pre-R83 bytes"
        }
        record(rows, "default", plain)

        // --- both values on a heading, after w:pStyle ---------------------------------------------
        val spacedHeading = listOf(heading(1, "Spaced heading", 240, 120))
        val spacedHeadingBytes = write(out, "heading-spacing", spacedHeading)
        check(body(spacedHeadingBytes).startsWith(
            "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/><w:spacing w:before=\"240\" w:after=\"120\"/></w:pPr>",
        )) { "w:spacing follows w:pStyle: ${body(spacedHeadingBytes)}" }
        record(rows, "heading-spacing", spacedHeading)

        // --- one attribute at a time ---------------------------------------------------------------
        val beforeOnly = listOf(paragraph("before only", 120, null))
        val beforeBytes = write(out, "paragraph-before", beforeOnly)
        check(body(beforeBytes).contains("<w:spacing w:before=\"120\"/>")) {
            "a lone w:before must not invent w:after: ${body(beforeBytes)}"
        }
        record(rows, "paragraph-before", beforeOnly)

        val afterOnly = listOf(paragraph("after only", null, 360))
        val afterBytes = write(out, "paragraph-after", afterOnly)
        check(body(afterBytes).contains("<w:spacing w:after=\"360\"/>")) {
            "a lone w:after must not invent w:before: ${body(afterBytes)}"
        }
        record(rows, "paragraph-after", afterOnly)

        // --- explicit zero is a declaration, not an omission ---------------------------------------
        val zero = listOf(paragraph("zero spacing", 0, 0))
        val zeroBytes = write(out, "zero", zero)
        check(body(zeroBytes).contains("<w:spacing w:before=\"0\" w:after=\"0\"/>")) {
            "an explicit zero is emitted: ${body(zeroBytes)}"
        }
        record(rows, "zero", zero)

        // --- spacing and alignment together: CT_PPr order is spacing then jc ------------------------
        val both = listOf(paragraph("centred and spaced", 120, 240, DocxPackage.Align.CENTER))
        val bothBytes = write(out, "spacing-align", both)
        check(body(bothBytes).contains(
            "<w:pPr><w:spacing w:before=\"120\" w:after=\"240\"/><w:jc w:val=\"center\"/></w:pPr>",
        )) { "w:spacing precedes w:jc: ${body(bothBytes)}" }
        record(rows, "spacing-align", both)

        // --- spacing beside every other block kind --------------------------------------------------
        val mixed = listOf(
            heading(2, "Mixed", 240, 0),
            paragraph("spaced body", 60, 180, DocxPackage.Align.RIGHT),
            DocxPackage.Block.ListItem(false, 0, listOf(DocxPackage.Run("bullet"))),
            DocxPackage.Block.BlockQuote(listOf(DocxPackage.Run("quoted"))),
            DocxPackage.Block.CodeBlock(listOf("val x = 1")),
            DocxPackage.Block.Table(listOf(listOf("A", "B")), columnWidthsTwips = listOf(2_400, 2_400)),
        )
        val mixedBytes = write(out, "mixed", mixed)
        val mixedBody = body(mixedBytes)
        check(mixedBody.split("w:spacing").size == 3) {
            "exactly two w:spacing elements in a mixed document (found ${mixedBody.split("w:spacing").size - 1})"
        }
        check("<w:tblPr><w:tblW w:w=\"4800\"" in mixedBody) { "the sized table keeps its geometry" }
        record(rows, "mixed", mixed)

        // --- spacing beside section layout and a hyperlink -------------------------------------------
        val layoutDoc = listOf(
            heading(3, "Laid out", 200, 200),
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("linked", href = "https://example.org")), null, 0, 120),
        )
        val layout = DocxPackage.Layout(pageSetup = DocxPackage.PageSetup.a4(), footer = "Page {PAGE}")
        val layoutBytes = write(out, "layout-spacing", layoutDoc, layout)
        check(DocxPackage.readLayout(layoutBytes) == layout) { "spacing does not disturb the section layout" }
        check("w:hyperlink" in text(layoutBytes)) { "the hyperlink survives" }
        record(rows, "layout-spacing", layoutDoc)

        // --- boundaries and refusals ------------------------------------------------------------------
        assertions++
        check(paragraph("max", 31_680, 31_680).spaceAfterTwips == 31_680) { "the ceiling is accepted" }
        fails("negative before") { paragraph("bad", -1, null) }
        fails("before above the ceiling") { paragraph("bad", 31_681, null) }
        fails("negative after") { paragraph("bad", null, -1) }
        fails("heading spacing above the ceiling") { heading(1, "bad", null, 31_681) }
        assertions++
        check(paragraph("edge", 0, 0).spaceBeforeTwips == 0) { "zero is accepted" }
        assertions++
        check(DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x"))) ==
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x")), null, null, null)) {
            "the default arguments keep the plain Paragraph shape"
        }

        File(out, "spacing.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R83_WRITER_SPACING_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size}")
    }
}
