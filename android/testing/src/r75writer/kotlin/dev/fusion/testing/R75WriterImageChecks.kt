// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.StructuredWriterDocx
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipInputStream

/**
 * R75 Writer image contracts. Host JVM only: this compiles and executes the production DOCX
 * writer with caller-supplied raster bytes, then re-reads its own output. Independent
 * verification with python-docx and raw OPC XML lives in tools/check_r75_writer_images.py;
 * neither is Word rendering evidence and neither proves anything about Android.
 */
object R75WriterImageChecks {
    private var assertions = 0
    private fun check(ok: Boolean, label: String) {
        assertions++
        require(ok) { "FAILED: $label" }
    }

    /** Minimal standards-conforming PNG encoder: signature, IHDR, one filtered IDAT, IEND. */
    private fun png(width: Int, height: Int, seed: Int): ByteArray {
        val raw = ByteArrayOutputStream()
        for (y in 0 until height) {
            raw.write(0) // filter type 0 (none) for the single scanline group
            for (x in 0 until width) {
                raw.write((x * 37 + seed) and 0xFF)
                raw.write((y * 53 + seed * 3) and 0xFF)
                raw.write(((x + y) * 29 + seed) and 0xFF)
            }
        }
        val deflated = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out).use { it.write(raw.toByteArray()) }
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
            out.write(ByteBuffer.allocate(4).putInt(data.size).array())
            out.write(typeBytes)
            out.write(data)
            val crc = CRC32().apply { update(typeBytes); update(data) }.value
            out.write(ByteBuffer.allocate(4).putInt(crc.toInt()).array())
        }
        chunk("IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height)
            .put(8.toByte()).put(2.toByte()).put(0.toByte()).put(0.toByte()).put(0.toByte()).array())
        chunk("IDAT", deflated)
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    /**
     * A real 16x16 baseline JPEG (303 bytes) produced once with ImageMagick 6.9 from
     * `convert -size 16x16 gradient:red-blue -quality 85 tiny.jpg`. Stored as data, not code:
     * the writer must accept a genuine second container, not only the shape it can synthesise.
     */
    private val JPEG_16 = Base64.getDecoder().decode(
        "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAUDBAQEAwUEBAQFBQUGBwwIBwcHBw8LCwkMEQ8SEhEP" +
            "ERETFhwXExQaFRERGCEYGh0dHx8fExciJCIeJBweHx7/2wBDAQUFBQcGBw4ICA4eFBEUHh4eHh4e" +
            "Hh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh7/wAARCAAQABADASIA" +
            "AhEBAxEB/8QAFgABAQEAAAAAAAAAAAAAAAAAAAYH/8QAFxAAAwEAAAAAAAAAAAAAAAAAABRhYv/E" +
            "ABQBAQAAAAAAAAAAAAAAAAAAAAf/xAAWEQADAAAAAAAAAAAAAAAAAAAAF2L/2gAMAwEAAhEDEQA/" +
            "AMbe0HtEs/Q/REVkCqybP//Z"
    )

    private fun mediaParts(docx: ByteArray): Map<String, ByteArray> {
        val parts = LinkedHashMap<String, ByteArray>()
        ZipInputStream(docx.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("word/media/")) parts[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return parts
    }

    private fun text(docx: ByteArray): String = String(docx, StandardCharsets.ISO_8859_1)

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }

        val small = png(4, 3, 7)
        val wide = png(8, 2, 11)
        val imageSmall = DocxPackage.Image.of(small)
        check(imageSmall.format == DocxPackage.ImageFormat.PNG, "PNG detected from magic bytes")
        check(imageSmall.widthPx == 4 && imageSmall.heightPx == 3, "PNG IHDR dimensions")
        check(imageSmall.widthEmu == 4 * 9_525 && imageSmall.heightEmu == 3 * 9_525, "PNG EMU derivation")
        check(DocxPackage.Image.of(wide).widthPx == 8, "second PNG dimensions")

        val jpegImage = DocxPackage.Image.of(JPEG_16)
        check(jpegImage.format == DocxPackage.ImageFormat.JPEG, "JPEG detected from magic bytes")
        check(jpegImage.widthPx == 16 && jpegImage.heightPx == 16, "JPEG SOF dimensions")

        // 1. Structured conversion: a marker-only line becomes a picture, text around it survives.
        val source = listOf(
            "# Pictures",
            "Above the figure.",
            "![a gradient square](square)",
            "Between the figures.",
            "![second](wide)",
            "![missing key](absent)",
            "[[pagebreak]]",
            "After the break.",
        ).joinToString("\n")
        val docx = StructuredWriterDocx.write(
            source, numberedLines = false, strikeSpans = false, restartOrderedGroups = false,
            pipeTables = false, images = mapOf("square" to small, "wide" to wide),
        )
        File(out, "images.docx").writeBytes(docx)

        val parsed = DocxPackage.read(docx)
        val pictures = parsed.blocks.filterIsInstance<DocxPackage.Block.Picture>()
        check(pictures.size == 2, "two pictures produced, got ${pictures.size}")
        check(pictures[0].image == imageSmall && pictures[0].alt == "a gradient square", "first picture round trip")
        check(pictures[1].image.widthPx == 8 && pictures[1].alt == "second", "second picture round trip")
        check(pictures[0].widthEmu == 4 * 9_525 && pictures[1].heightEmu == 2 * 9_525, "extents round trip")
        check(parsed.blocks.indexOfFirst { it is DocxPackage.Block.Picture } == 2, "picture keeps its block position")
        check(parsed.blocks.any { it == DocxPackage.Block.PageBreak }, "page break still parsed next to pictures")
        val literal = parsed.blocks.filterIsInstance<DocxPackage.Block.Paragraph>()
            .joinToString("\n") { block -> block.runs.joinToString("") { it.text } }
        check(literal.contains("![missing key](absent)"), "unknown image key stays literal")
        check(literal.contains("Above the figure.") && literal.contains("Between the figures."), "surrounding text survives")

        // 2. Both formats in one package, and identical bytes are stored once.
        val mixed = DocxPackage.Document(blocks = listOf(
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("mixed"))),
            DocxPackage.Block.Picture(imageSmall, "same bytes again"),
            DocxPackage.Block.Picture(jpegImage, "a jpeg"),
            DocxPackage.Block.Picture(imageSmall, "third reference to the same png"),
        ))
        val mixedBytes = DocxPackage.write(mixed)
        File(out, "mixed.docx").writeBytes(mixedBytes)
        val media = mediaParts(mixedBytes)
        check(media.size == 2, "identical images stored once, got ${media.keys}")
        check(media.containsKey("word/media/image1.png") && media.containsKey("word/media/image2.jpeg"),
            "media part names follow first use: ${media.keys}")
        check(media.getValue("word/media/image1.png").contentEquals(small), "png bytes stored verbatim")
        check(media.getValue("word/media/image2.jpeg").contentEquals(JPEG_16), "jpeg bytes stored verbatim")
        val mixedBack = DocxPackage.read(mixedBytes)
        check(mixedBack.blocks == mixed.blocks, "mixed document round trip including jpeg and dedupe")
        check(text(mixedBytes).contains("descr=\"a jpeg\""), "alt text written into wp:docPr")

        // 3. Links and pictures coexist with disjoint relationship ids.
        val both = DocxPackage.Document(blocks = listOf(
            DocxPackage.Block.Paragraph(listOf(
                DocxPackage.Run("see "),
                DocxPackage.Run("the docs", href = "https://example.org/docs"),
            )),
            DocxPackage.Block.Picture(imageSmall, "figure"),
        ))
        val bothBytes = DocxPackage.write(both)
        File(out, "links-and-images.docx").writeBytes(bothBytes)
        val bothRels = text(bothBytes)
        check(bothRels.contains("rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"),
            "hyperlink keeps rId3 when a picture follows")
        check(bothRels.contains("rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"),
            "picture takes the next relationship id")
        check(DocxPackage.read(bothBytes).blocks == both.blocks, "link+picture round trip")

        // 3b. Alt text survives XML escaping, and caller-chosen extents survive the round trip.
        val escaped = DocxPackage.Document(blocks = listOf(
            DocxPackage.Block.Picture(imageSmall, "A & B \"quoted\" <tag>"),
            DocxPackage.Block.Picture(imageSmall, "scaled", widthEmu = 952_500, heightEmu = 476_250),
        ))
        val escapedBytes = DocxPackage.write(escaped)
        File(out, "escaped-alt.docx").writeBytes(escapedBytes)
        val escapedBack = DocxPackage.read(escapedBytes)
        check(escapedBack.blocks == escaped.blocks, "alt text and explicit extents round trip")
        check(text(escapedBytes).contains("descr=\"A &amp; B &quot;quoted&quot; &lt;tag&gt;\""),
            "alt text is XML-escaped in wp:docPr")
        check(text(escapedBytes).contains("cx=\"952500\""), "caller extent is written verbatim")

        // 4. Determinism: the same input produces the same bytes, twice.
        val again = DocxPackage.write(mixed)
        check(again.contentEquals(mixedBytes), "write is deterministic")

        // 5. Boundaries: every refusal is an exception, not a silent fallback.
        check(runCatching { DocxPackage.Image.of(ByteArray(0)) }.isFailure, "empty bytes refused")
        check(runCatching { DocxPackage.Image.of(byteArrayOf(1, 2, 3)) }.isFailure, "unknown container refused")
        check(runCatching { DocxPackage.Image.of(small.copyOf(small.size + DocxPackage.MAX_IMAGE_BYTES)) }.isFailure,
            "oversized image refused")
        check(runCatching { DocxPackage.Image.of(small.copyOf(20)) }.isFailure, "truncated PNG refused")
        val zeroWidth = small.copyOf()
        ByteBuffer.wrap(zeroWidth).putInt(16, 0)
        check(runCatching { DocxPackage.Image.of(zeroWidth) }.isFailure, "zero-width PNG refused")
        // Documented limit: dimensions come from the IHDR/SOF header, not from decoding the pixel
        // stream. A header that disagrees with its compressed body is therefore *accepted*; Word
        // does its own decode. This assertion pins that boundary instead of pretending otherwise.
        val relabelled = small.copyOf()
        ByteBuffer.wrap(relabelled).putInt(16, 99)
        check(DocxPackage.Image.of(relabelled).widthPx == 99, "declared IHDR width is what the writer trusts")
        check(DocxPackage.Image.of(relabelled).widthEmu == 99 * 9_525, "declared width drives the extent")
        check(runCatching { DocxPackage.Block.Picture(imageSmall, "x", widthEmu = 0) }.isFailure, "zero extent refused")
        check(runCatching {
            DocxPackage.Block.Picture(imageSmall, "x", widthEmu = DocxPackage.MAX_IMAGE_EMU + 1)
        }.isFailure, "oversized extent refused")
        check(runCatching { DocxPackage.Block.Picture(imageSmall, "a".repeat(1_025)) }.isFailure, "long alt text refused")
        check(runCatching {
            DocxPackage.Document(blocks = (0..DocxPackage.MAX_IMAGES).map {
                DocxPackage.Block.Picture(DocxPackage.Image.of(png(1, 1, it)), "x")
            }).let { DocxPackage.write(it) }
        }.isFailure, "image count bound enforced on write")
        val truncatedJpeg = JPEG_16.copyOf(6)
        check(runCatching { DocxPackage.Image.of(truncatedJpeg) }.isFailure, "headerless JPEG refused")

        println("R75_WRITER_IMAGES_OK assertions=$assertions artifacts=${out.absolutePath}")
    }
}
