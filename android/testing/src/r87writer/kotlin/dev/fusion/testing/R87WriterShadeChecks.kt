// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import java.io.File

/**
 * R87 Writer paragraph shading plus the tab-character path: `w:shd` (solid `clear` pattern, `w:fill`
 * carrying the caller's 6-hex background) placed before `w:tabs` in CT_PPr order, and a `\t` inside a run
 * emitted as `<w:tab/>` so that R86's tab stops are actually reachable from text.
 *
 * Host JVM only. The probe writes real `.docx` packages plus `shade.tsv`; the independent readers are
 * tools/check_r87_writer_shade.py (raw OPC + python-docx) and tools/check_r87_docx_render.py, which hands
 * the same packages to the docx-preview HTML renderer under Node and reads the CSS it produces. Neither is
 * Word, and no caller in the app sets these fields yet.
 */
object R87WriterShadeChecks {
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

    /** Reads one OPC part back out of a package. */
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

    private fun run(value: String) = DocxPackage.Run(value)

    private fun paragraph(
        value: String,
        shading: String? = null,
        tabs: List<DocxPackage.TabStop> = emptyList(),
        alignment: DocxPackage.Align? = null,
        left: Int? = null,
        firstLine: Int? = null,
        pageBreakBefore: Boolean? = null,
        widowControl: Boolean? = null,
        runs: List<DocxPackage.Run>? = null,
    ) = DocxPackage.Block.Paragraph(
        runs = runs ?: listOf(run(value)),
        alignment = alignment,
        indentLeftTwips = left,
        firstLineTwips = firstLine,
        pageBreakBefore = pageBreakBefore,
        widowControl = widowControl,
        tabStops = tabs,
        shadingRgb = shading,
    )

    private fun heading(level: Int, value: String, shading: String? = null) =
        DocxPackage.Block.Heading(level = level, runs = listOf(run(value)), shadingRgb = shading)

    private fun tabIntent(stops: List<DocxPackage.TabStop>): String =
        if (stops.isEmpty()) "-" else stops.joinToString(";") { s ->
            "${s.positionTwips}:${s.align.token}:${s.leader?.token ?: "none"}"
        }

    /** Tabs are escaped so one intent row stays one TSV line. */
    private fun escaped(value: String): String = value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    /**
     * `compare = false` is used for the case-normalisation fixture, whose declared value is deliberately
     * not the canonical one; that fixture asserts the canonical form separately.
     */
    private fun write(
        out: File,
        name: String,
        blocks: List<DocxPackage.Block>,
        layout: DocxPackage.Layout? = null,
        compare: Boolean = true,
    ): ByteArray {
        val document = DocxPackage.Document(title = name, blocks = blocks, layout = layout)
        val bytes = DocxPackage.write(document)
        File(out, "$name.docx").writeBytes(bytes)
        val readBack = DocxPackage.read(bytes)
        if (compare) check(readBack.blocks == blocks) { "$name block round trip: ${readBack.blocks}" }
        if (layout != null) check(DocxPackage.readLayout(bytes) == layout) { "$name layout round trip" }
        return bytes
    }

    /** One intent row per body `w:p`, in body order (the order a reader reports paragraphs in). */
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
            val shading = when (block) {
                is DocxPackage.Block.Heading -> block.shadingRgb
                is DocxPackage.Block.Paragraph -> block.shadingRgb
                else -> null
            }
            val tabs = when (block) {
                is DocxPackage.Block.Heading -> block.tabStops
                is DocxPackage.Block.Paragraph -> block.tabStops
                else -> emptyList()
            }
            val bodyText = when (block) {
                is DocxPackage.Block.Heading -> block.runs.joinToString("") { it.text }
                is DocxPackage.Block.Paragraph -> block.runs.joinToString("") { it.text }
                is DocxPackage.Block.ListItem -> block.runs.joinToString("") { it.text }
                is DocxPackage.Block.BlockQuote -> block.runs.joinToString("") { it.text }
                is DocxPackage.Block.CodeBlock -> block.lines.joinToString("\n")
                else -> ""
            }
            rows.add(
                listOf(
                    name, index, kind, shading?.uppercase() ?: "-", tabIntent(tabs), escaped(bodyText),
                ).joinToString("\t"),
            )
            index++
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: nothing declared, nothing emitted ---------------------------------
        val plainBlocks = listOf(heading(1, "Plain"), paragraph("Body"))
        val plain = write(out, "plain", plainBlocks)
        check("<w:shd" !in body(plain)) { "a block without a fill must not emit w:shd" }
        record(rows, "plain", plainBlocks)

        // --- shading on both block kinds ------------------------------------------------------------
        val shadedBlocks = listOf(heading(2, "Callout", shading = "ABCDEF"), paragraph("Warn", shading = "123456"))
        val shaded = write(out, "shaded", shadedBlocks)
        val shadedBody = body(shaded)
        check("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"ABCDEF\"/>" in shadedBody) {
            "the heading fill is written verbatim: $shadedBody"
        }
        check("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"123456\"/>" in shadedBody) {
            "the paragraph fill is written verbatim"
        }
        check(shadedBody.indexOf("ABCDEF") < shadedBody.indexOf("123456")) { "heading precedes paragraph" }
        record(rows, "shaded", shadedBlocks)

        // --- lower-case input is normalised on the wire --------------------------------------------
        val lowerBlocks = listOf(paragraph("Lower", shading = "abcdef"))
        val lower = write(out, "shade-lower", lowerBlocks, compare = false)
        check("w:fill=\"ABCDEF\"" in body(lower)) { "lower-case hex is upper-cased on write" }
        check(DocxPackage.read(lower).blocks.filterIsInstance<DocxPackage.Block.Paragraph>().single()
            .shadingRgb == "ABCDEF") { "the reader normalises the fill too" }
        record(rows, "shade-lower", lowerBlocks)

        // --- CT_PPr order: shd precedes tabs, tabs precede spacing ---------------------------------
        val combinedBlocks = listOf(
            paragraph(
                "Section\t1",
                shading = "00B050",
                tabs = listOf(DocxPackage.TabStop(7200, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.DOT)),
            ),
        )
        val combined = write(out, "shade-and-tabs", combinedBlocks)
        val combinedBody = body(combined)
        check(combinedBody.indexOf("<w:shd") in 0 until combinedBody.indexOf("<w:tabs>")) {
            "w:shd must precede w:tabs: $combinedBody"
        }
        // --- the tab character itself: R86 stops are only reachable through <w:tab/> ----------------
        check("<w:tab w:val=\"right\" w:pos=\"7200\" w:leader=\"dot\"/>" in combinedBody) { "the stop is declared" }
        check("<w:tab/><w:t xml:space=\"preserve\">1</w:t>" in combinedBody) {
            "a \\t inside a run becomes <w:tab/> between two w:t segments: $combinedBody"
        }
        val combinedRead = DocxPackage.read(combined).blocks.filterIsInstance<DocxPackage.Block.Paragraph>().single()
        check(combinedRead.runs.joinToString("") { it.text } == "Section\t1") {
            "the reader rejoins the tab character: ${combinedRead.runs}"
        }
        record(rows, "shade-and-tabs", combinedBlocks)

        // --- the canonical table-of-contents line, two stops, leader + plain ----------------------
        val tocBlocks = listOf(
            DocxPackage.Block.Paragraph(
                runs = listOf(run("Chapter 1"), run("\t"), run("7")),
                tabStops = listOf(
                    DocxPackage.TabStop(1440, DocxPackage.TabAlign.LEFT, null),
                    DocxPackage.TabStop(7200, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.DOT),
                ),
            ),
        )
        val toc = write(out, "toc", tocBlocks)
        val tocBody = body(toc)
        check(
            "<w:tabs><w:tab w:val=\"left\" w:pos=\"1440\"/>" +
                "<w:tab w:val=\"right\" w:pos=\"7200\" w:leader=\"dot\"/></w:tabs>" in tocBody,
        ) { "both stops in ascending order: $tocBody" }
        check("<w:r><w:t xml:space=\"preserve\">Chapter 1</w:t></w:r><w:r><w:tab/></w:r>" in tocBody) {
            "the tab run sits between the two texts: $tocBody"
        }
        record(rows, "toc", tocBlocks)

        // --- shading is not leaked into other block kinds ------------------------------------------
        val mixedBlocks = listOf(
            heading(1, "Mixed", shading = "FFFF00"),
            DocxPackage.Block.ListItem(ordered = false, level = 0, runs = listOf(run("bullet"))),
            DocxPackage.Block.BlockQuote(runs = listOf(run("quoted"))),
            DocxPackage.Block.CodeBlock(listOf("val x = 1")),
            DocxPackage.Block.Table(rows = listOf(listOf("a", "b"), listOf("c", "d"))),
        )
        val mixed = write(out, "mixed", mixedBlocks)
        val mixedBody = body(mixed)
        check(mixedBody.count { it == '<' } > 0) { "mixed body is non-trivial" }
        check(mixedBody.split("<w:shd").size - 1 == 1) { "exactly one w:shd in the mixed document: $mixedBody" }
        record(rows, "mixed", mixedBlocks)

        // --- boundaries: every other property still composes with a fill ---------------------------
        val boundaryBlocks = listOf(
            paragraph(
                "Boundary",
                shading = "FFFFFF",
                tabs = listOf(DocxPackage.TabStop(0, DocxPackage.TabAlign.CLEAR, null)),
                alignment = DocxPackage.Align.JUSTIFY,
                left = 720,
                firstLine = -240,
                pageBreakBefore = false,
                widowControl = true,
            ),
        )
        val boundary = write(out, "boundary", boundaryBlocks)
        val boundaryBody = body(boundary)
        check("w:fill=\"FFFFFF\"" in boundaryBody) { "white is a real fill, not 'no fill'" }
        check(boundaryBody.indexOf("<w:shd") < boundaryBody.indexOf("<w:tabs>")) { "order holds at the boundaries" }
        record(rows, "boundary", boundaryBlocks)

        // --- refusals ------------------------------------------------------------------------------
        for (bad in listOf("ABCDE", "ABCDEFG", "#ABCDEF", "GGGGGG", "")) {
            fails("shadingRgb=$bad") { paragraph("x", shading = bad) }
            fails("heading shadingRgb=$bad") { heading(1, "x", shading = bad) }
        }

        // --- import leniency is byte-level: a themed or patterned fill is dropped, not guessed -----
        val themed = rezip(shaded, "word/document.xml") { xml ->
            xml.replace("w:fill=\"ABCDEF\"", "w:fill=\"theme\"")
        }
        val themedRead = DocxPackage.read(themed).blocks.filterIsInstance<DocxPackage.Block.Heading>().single()
        check(themedRead.shadingRgb == null) { "a themed fill is dropped on import, was ${themedRead.shadingRgb}" }
        val patterned = rezip(shaded, "word/document.xml") { xml ->
            xml.replace("w:val=\"clear\" w:color=\"auto\" w:fill=\"ABCDEF\"", "w:val=\"pct20\" w:color=\"auto\" w:fill=\"ABCDEF\"")
        }
        val patternedRead = DocxPackage.read(patterned).blocks.filterIsInstance<DocxPackage.Block.Heading>().single()
        check(patternedRead.shadingRgb == null) {
            "a non-solid pattern is dropped on import rather than flattened, was ${patternedRead.shadingRgb}"
        }
        // The rezip path must still be a readable package, not a truncated one.
        check(part(themed, "word/document.xml").contains("<w:body>")) { "the patched package keeps its body" }

        File(out, "shade.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R87_WRITER_SHADE_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size}")
    }
}
