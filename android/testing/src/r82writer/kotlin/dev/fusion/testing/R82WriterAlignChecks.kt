// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import java.io.File

/**
 * R82 Writer paragraph-alignment contracts: `Block.Paragraph.alignment` and
 * `Block.Heading.alignment` emit `w:jc` inside `w:pPr` (after `w:pStyle`, per CT_PPr) and read back.
 *
 * Host JVM only. The probe writes real `.docx` packages plus `align.tsv`; the independent reader is
 * tools/check_r82_writer_align.py, which opens the packages with python-docx (whose
 * `paragraph.alignment` is a real second opinion) and inspects the raw OPC parts. Nothing here is
 * Word rendering evidence, and no caller sets alignment yet.
 */
object R82WriterAlignChecks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun body(bytes: ByteArray): String =
        text(bytes).substringAfter("<w:body>").substringBefore("<w:sectPr")

    private fun para(paragraphText: String, alignment: DocxPackage.Align?) =
        DocxPackage.Block.Paragraph(listOf(DocxPackage.Run(paragraphText)), alignment)

    private fun heading(level: Int, headingText: String, alignment: DocxPackage.Align?) =
        DocxPackage.Block.Heading(level, listOf(DocxPackage.Run(headingText)), alignment)

    private fun write(out: File, name: String, blocks: List<DocxPackage.Block>, layout: DocxPackage.Layout? = null): ByteArray {
        val document = DocxPackage.Document(title = name, blocks = blocks, layout = layout)
        val bytes = DocxPackage.write(document)
        File(out, "$name.docx").writeBytes(bytes)
        check(DocxPackage.read(bytes).blocks == blocks) { "$name block round trip: ${DocxPackage.read(bytes).blocks}" }
        return bytes
    }

    /** One intent row per body `w:p`, in body order: the same order python-docx reports paragraphs in. */
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
                is DocxPackage.Block.PageBreak -> "pagebreak"
                else -> "other"
            }
            val alignment = when (block) {
                is DocxPackage.Block.Heading -> block.alignment
                is DocxPackage.Block.Paragraph -> block.alignment
                else -> null
            }
            rows.add(listOf(name, index, kind, alignment?.token ?: "-").joinToString("\t"))
            index++
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: no alignment means no w:jc at all -------------------------------
        val plain = listOf(
            heading(1, "Default heading", null),
            para("Default body one.", null),
            para("Default body two.", null),
        )
        val plainBytes = write(out, "default", plain)
        check("w:jc" !in body(plainBytes)) { "a document without alignment must not emit w:jc: ${body(plainBytes)}" }
        check(body(plainBytes).startsWith("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>")) {
            "the default heading keeps its pre-R82 prefix"
        }
        check(body(plainBytes).contains("<w:p><w:r><w:t xml:space=\"preserve\">Default body one.</w:t></w:r></w:p>")) {
            "the default paragraph keeps its pre-R82 bytes"
        }
        record(rows, "default", plain)

        // --- a centred heading: w:jc after w:pStyle -----------------------------------------------
        val centered = listOf(heading(1, "Centred heading", DocxPackage.Align.CENTER))
        val centeredBytes = write(out, "heading-center", centered)
        check(body(centeredBytes).startsWith(
            "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/><w:jc w:val=\"center\"/></w:pPr>",
        )) { "w:jc must follow w:pStyle inside w:pPr: ${body(centeredBytes)}" }
        check("Heading1\"/><w:jc" in body(centeredBytes)) { "pStyle precedes jc" }
        record(rows, "heading-center", centered)

        // --- each paragraph alignment, and justified text uses Word's `both` -----------------------
        val rightBytes = write(out, "paragraph-right", listOf(para("Right aligned.", DocxPackage.Align.RIGHT)))
        check(body(rightBytes).contains("<w:p><w:pPr><w:jc w:val=\"right\"/></w:pPr>")) { "right alignment" }
        record(rows, "paragraph-right", listOf(para("Right aligned.", DocxPackage.Align.RIGHT)))

        val justifyBytes = write(out, "paragraph-justify", listOf(para("Justified body.", DocxPackage.Align.JUSTIFY)))
        check(body(justifyBytes).contains("<w:p><w:pPr><w:jc w:val=\"both\"/></w:pPr>")) {
            "justify must be emitted as CT_Jc `both`: ${body(justifyBytes)}"
        }
        record(rows, "paragraph-justify", listOf(para("Justified body.", DocxPackage.Align.JUSTIFY)))

        val leftBytes = write(out, "paragraph-left", listOf(para("Left aligned.", DocxPackage.Align.LEFT)))
        check(body(leftBytes).contains("<w:p><w:pPr><w:jc w:val=\"left\"/></w:pPr>")) {
            "an explicit left alignment is declared, not silently dropped"
        }
        record(rows, "paragraph-left", listOf(para("Left aligned.", DocxPackage.Align.LEFT)))

        // --- all four in one document, in order ---------------------------------------------------
        val all = listOf(
            para("one", DocxPackage.Align.LEFT),
            para("two", DocxPackage.Align.CENTER),
            para("three", DocxPackage.Align.RIGHT),
            para("four", DocxPackage.Align.JUSTIFY),
        )
        val allBytes = write(out, "all-four", all)
        val allBody = body(allBytes)
        check(listOf("\"left\"", "\"center\"", "\"right\"", "\"both\"").all { it in allBody }) {
            "all four CT_Jc tokens appear: $allBody"
        }
        check(allBody.substringAfter("<w:jc w:val=\"left\"/>").substringAfter("<w:jc w:val=\"center\"/>")
            .contains("w:jc w:val=\"right\"")) { "alignments stay in block order" }
        record(rows, "all-four", all)

        // --- alignment beside every other block kind, and beside a sized table ---------------------
        val mixed = listOf(
            heading(2, "Mixed section", DocxPackage.Align.CENTER),
            para("Centred body.", DocxPackage.Align.CENTER),
            DocxPackage.Block.ListItem(false, 0, listOf(DocxPackage.Run("bullet"))),
            DocxPackage.Block.BlockQuote(listOf(DocxPackage.Run("quoted"))),
            DocxPackage.Block.CodeBlock(listOf("val x = 1")),
            DocxPackage.Block.Table(listOf(listOf("A", "B")), columnWidthsTwips = listOf(2_400, 2_400)),
            para("After the table.", DocxPackage.Align.RIGHT),
        )
        val mixedBytes = write(out, "mixed", mixed)
        val mixedBody = body(mixedBytes)
        check(mixedBody.split("w:jc").size == 4) {
            "exactly two jc elements in a mixed document (found ${mixedBody.split("w:jc").size - 1})"
        }
        check("<w:tblPr><w:tblW w:w=\"4800\"" in mixedBody) { "the sized table keeps its geometry" }
        check(para("x", null) == DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x")))) {
            "the default argument keeps the two-argument Paragraph shape"
        }
        check(heading(2, "x", null) == DocxPackage.Block.Heading(2, listOf(DocxPackage.Run("x")))) {
            "the default argument keeps the two-argument Heading shape"
        }
        record(rows, "mixed", mixed)

        // --- alignment beside section layout and a hyperlink ---------------------------------------
        val layoutDoc = listOf(
            heading(3, "Laid out", DocxPackage.Align.RIGHT),
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("linked", href = "https://example.org"))),
            para("centred under a header", DocxPackage.Align.CENTER),
        )
        val layout = DocxPackage.Layout(pageSetup = DocxPackage.PageSetup.a4(), header = "Above", footer = "Below {PAGE}")
        val layoutBytes = write(out, "layout-align", layoutDoc, layout)
        check(DocxPackage.readLayout(layoutBytes) == layout) { "alignment does not disturb the section layout" }
        check("w:hyperlink" in text(layoutBytes)) { "the hyperlink survives" }
        record(rows, "layout-align", layoutDoc)

        // --- tokens are exactly the four CT_Jc values this writer supports --------------------------
        check(DocxPackage.Align.entries.map { it.token } == listOf("left", "center", "right", "both")) {
            "the emitted token set is ${DocxPackage.Align.entries.map { it.token }}"
        }

        File(out, "align.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R82_WRITER_ALIGN_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size}")
    }
}
