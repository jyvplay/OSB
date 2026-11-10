// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.StructuredWriterDocx
import java.io.File

/**
 * R74 Writer hyperlink and page-break contracts. Host JVM only: this compiles and executes the
 * production DOCX writer, then re-reads its own bytes. Independent verification with python-docx
 * lives in tools/check_r74_writer_links.py; neither is Office rendering evidence.
 */
object R74WriterLinkChecks {
    private var assertions = 0

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected <artifact directory>" }
        val artifacts = File(args[0])
        artifacts.mkdirs()

        val source = listOf(
            "# Links",
            "Visit [Arena](https://example.org/a?x=1&y=2) and [mail](mailto:someone@example.org).",
            "Rejected scheme stays literal: [bad](javascript:alert(1)) and [ftp](ftp://example.org).",
            "Unterminated [label](https://example.org stays literal too.",
            "Escaped \\[label](https://example.org) stays literal.",
            "**Bold** and `code` with a [link](https://example.org/plain).",
            "[[pagebreak]]",
            "After the break.",
        ).joinToString("\n")
        val bytes = StructuredWriterDocx.write(source, numberedLines = false, strikeSpans = true,
            restartOrderedGroups = false, pipeTables = true)
        File(artifacts, "links.docx").writeBytes(bytes)

        val parsed = DocxPackage.read(bytes)
        val hrefs = parsed.blocks.flatMap { block ->
            when (block) {
                is DocxPackage.Block.Heading -> block.runs
                is DocxPackage.Block.Paragraph -> block.runs
                is DocxPackage.Block.ListItem -> block.runs
                is DocxPackage.Block.BlockQuote -> block.runs
                else -> emptyList()
            }
        }.filter { it.href != null }
        check(hrefs.size == 3) { "expected 3 hyperlink runs, got ${hrefs.size}: ${hrefs.map { it.text to it.href }}" }
        check(hrefs[0].text == "Arena" && hrefs[0].href == "https://example.org/a?x=1&y=2") { "first link round-trip" }
        check(hrefs[1].href == "mailto:someone@example.org") { "mailto link round-trip" }
        check(hrefs[2].href == "https://example.org/plain") { "third link round-trip" }
        assertions += 4

        // Rejected schemes and malformed forms remain literal text, never silent hyperlinks.
        val texts = parsed.blocks.mapNotNull { block ->
            (block as? DocxPackage.Block.Paragraph)?.runs?.joinToString("") { it.text }
        }
        check(texts.any { it.contains("[bad](javascript:alert(1))") }) { "javascript link must stay literal" }
        check(texts.any { it.contains("[ftp](ftp://example.org)") }) { "ftp link must stay literal" }
        check(texts.any { it.contains("Unterminated [label](https://example.org stays literal too.") }) { "unterminated link literal" }
        check(texts.any { it.contains("Escaped \\[label](https://example.org) stays literal.") }) { "escaped link literal" }
        assertions += 4

        // Page break survives the round trip at the same position.
        val breakIndex = parsed.blocks.indexOfFirst { it == DocxPackage.Block.PageBreak }
        check(breakIndex > 0 && parsed.blocks.getOrNull(breakIndex + 1) is DocxPackage.Block.Paragraph) {
            "page break position ${breakIndex} in ${parsed.blocks.size} blocks"
        }
        assertions += 1

        // Idempotent re-write: blocks equal, bytes stable.
        val rewritten = DocxPackage.write(parsed)
        val parsedAgain = DocxPackage.read(rewritten)
        check(parsedAgain.blocks == parsed.blocks) { "re-write changed the block model" }
        check(DocxPackage.write(parsedAgain).contentEquals(rewritten)) { "re-write is not stable" }
        assertions += 2

        // Block API: relationships are shared for repeated targets and ordered by first appearance.
        val document = DocxPackage.Document(blocks = listOf(
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("one"), DocxPackage.Run("two", href = "https://example.org/same"))),
            DocxPackage.Block.PageBreak,
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("three", href = "https://example.org/same"),
                DocxPackage.Run("four", href = "mailto:x@example.org"))),
        ))
        val blockBytes = DocxPackage.write(document)
        File(artifacts, "blocks.docx").writeBytes(blockBytes)
        val rels = String(blockBytes, Charsets.ISO_8859_1)
        check(rels.contains("Target=\"https://example.org/same\" TargetMode=\"External\"")) { "shared target relationship" }
        val back = DocxPackage.read(blockBytes)
        check(back.blocks == document.blocks) { "block API round trip" }
        assertions += 2

        // Bounds: too many distinct targets, an oversized target and a bad scheme are refused.
        val many = DocxPackage.Document(blocks = (0..DocxPackage.MAX_HYPERLINKS).map {
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("x", href = "https://example.org/$it")))
        })
        check(runCatching { DocxPackage.write(many) }.isFailure) { "hyperlink count bound" }
        check(runCatching {
            DocxPackage.Run("x", href = "https://example.org/" + "a".repeat(DocxPackage.MAX_HREF_CHARS))
        }.isFailure) { "hyperlink length bound" }
        check(runCatching { DocxPackage.Run("x", href = "javascript:alert(1)") }.isFailure) { "scheme refused in the model" }
        check(runCatching { DocxPackage.Run("x", href = "https://example.org/\u0007") }.isFailure) { "control character refused" }
        assertions += 4

        // Text API refuses to emit a link whose target is over the limit instead of truncating it.
        val longTarget = "https://example.org/" + "b".repeat(DocxPackage.MAX_HREF_CHARS)
        val longText = StructuredWriterDocx.write("[long]($longTarget)")
        val longParsed = DocxPackage.read(longText)
        val longRuns = (longParsed.blocks.single() as DocxPackage.Block.Paragraph).runs
        check(longRuns.none { it.href != null }) { "oversized target must stay literal" }
        assertions += 1

        println("R74_WRITER_LINKS_OK assertions=$assertions artifacts=${artifacts.absolutePath}")
    }
}
