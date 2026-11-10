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
 * Clean-room bounded WordprocessingML `.docx` OPC package writer & structural reader over [BoundedXml].
 *
 * Advances `:format:ooxml` beyond read-only plain-text scanning (`Ooxml.extractDocxText`) into
 * round-trippable `.docx` authoring and structural import:
 * - Deterministic zero-epoch (`1980-01-01T00:00:00`) stored-ZIP output.
 * - Blocks: [Block.Heading] (`1..6`), [Block.Paragraph], [Block.ListItem] (`ordered`/`bullet`,
 *   `level 0..8` wired to `word/numbering.xml`), [Block.BlockQuote], [Block.CodeBlock], [Block.Table].
 * - Inline runs: [Run] (`bold`, `italic`, `underline`, `strike`, `code`).
 * - Fail-closed reader: bounded ZIP byte caps, [BoundedXml] parsing, and explicit refusal of
 *   VBA `vbaProject.bin` macro parts (`.docm` smuggling).
 */
object DocxPackage {
    const val SCOPE =
        "Bounded WordprocessingML .docx writer + structural BoundedXml reader (headings 1..6, styled runs, 9-level bullet/ordered lists, quotes, code blocks, rectangular tables); VBA macros refused; drawings/comments/track-changes/footnotes out of scope."

    const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val MAX_BLOCKS = 4_096
    const val MAX_TABLE_COLS = 64
    const val MAX_ZIP_BYTES = 16 * 1024 * 1024
    const val MAX_HYPERLINKS = 512
    const val MAX_HREF_CHARS = 2_048
    const val MAX_IMAGES = 64
    const val MAX_IMAGE_BYTES = 512 * 1024
    const val MAX_IMAGE_EDGE_PX = 4_096
    const val MAX_IMAGE_EMU = 40_000_000
    private const val EMU_PER_PIXEL = 9_525 // 96 dpi, the WordprocessingML convention
    const val WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
    const val DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val PICTURE_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture"
    private const val IMAGE_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
    private const val PICTURE_URI = "http://schemas.openxmlformats.org/drawingml/2006/picture"
    const val MAX_PART_BYTES = 1024 * 1024
    private const val BULLET_NUM_ID = 1
    private const val ORDERED_NUM_ID = 2

    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val code: Boolean = false,
        val href: String? = null,
    ) {
        init {
            validateXmlChars(text)
            if (href != null) validateHref(href)
        }
    }

    /** Formats this writer can embed. Detection is by magic bytes, never by caller assertion alone. */
    enum class ImageFormat(val extension: String, val contentType: String) {
        PNG("png", "image/png"),
        JPEG("jpeg", "image/jpeg"),
    }

    /**
     * A caller-supplied raster image. The writer never opens a file, a URL or a content provider:
     * bytes arrive from the caller, so the access model stays "pure function over in-memory data".
     * Equality is content-based, which is what makes round-trip comparison meaningful.
     */
    class Image private constructor(
        val bytes: ByteArray,
        val format: ImageFormat,
        val widthPx: Int,
        val heightPx: Int,
    ) {
        init {
            require(bytes.isNotEmpty()) { "image bytes must not be empty" }
            require(bytes.size <= MAX_IMAGE_BYTES) { "image exceeds $MAX_IMAGE_BYTES bytes" }
            require(widthPx in 1..MAX_IMAGE_EDGE_PX && heightPx in 1..MAX_IMAGE_EDGE_PX) {
                "image edge ${widthPx}x$heightPx outside 1..$MAX_IMAGE_EDGE_PX"
            }
            require(detect(bytes) == format) { "declared format ${format.extension} does not match the magic bytes" }
            val measured = measure(bytes)
                ?: throw IllegalArgumentException("image header is truncated or unsupported")
            require(measured.first == widthPx && measured.second == heightPx) {
                "declared ${widthPx}x$heightPx does not match the header ${measured.first}x${measured.second}"
            }
        }

        val widthEmu: Int get() = widthPx * EMU_PER_PIXEL
        val heightEmu: Int get() = heightPx * EMU_PER_PIXEL

        override fun equals(other: Any?): Boolean =
            other is Image && format == other.format && widthPx == other.widthPx &&
                heightPx == other.heightPx && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int {
            var h = format.hashCode()
            h = 31 * h + widthPx
            h = 31 * h + heightPx
            h = 31 * h + bytes.contentHashCode()
            return h
        }

        override fun toString(): String = "Image(${format.extension}, ${widthPx}x$heightPx, ${bytes.size}B)"

        companion object {
            /** Detects the container from its signature; returns null for anything else. */
            fun detect(bytes: ByteArray): ImageFormat? = when {
                bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
                    bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
                    bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte() -> ImageFormat.PNG
                bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                    bytes[2] == 0xFF.toByte() -> ImageFormat.JPEG
                else -> null
            }

            /** Reads the intrinsic pixel size from the PNG IHDR or a JPEG start-of-frame marker. */
            fun measure(bytes: ByteArray): Pair<Int, Int>? = when (detect(bytes)) {
                ImageFormat.PNG -> pngSize(bytes)
                ImageFormat.JPEG -> jpegSize(bytes)
                null -> null
            }

            fun of(bytes: ByteArray): Image {
                val format = detect(bytes) ?: throw IllegalArgumentException("unsupported image container")
                val (w, h) = measure(bytes) ?: throw IllegalArgumentException("image header is truncated")
                return Image(bytes, format, w, h)
            }

            /** Accepts exactly one PNG chunk layout: signature, IHDR with sane fields, and a length-bounded stream. */
            private fun pngSize(bytes: ByteArray): Pair<Int, Int>? {
                if (bytes.size < 24) return null
                if (bytes[12] != 'I'.code.toByte() || bytes[13] != 'H'.code.toByte() ||
                    bytes[14] != 'D'.code.toByte() || bytes[15] != 'R'.code.toByte()) return null
                val width = readInt(bytes, 16)
                val height = readInt(bytes, 20)
                if (width <= 0 || height <= 0) return null
                return width to height
            }

            /** Scans JPEG markers for a start-of-frame; the scan stops at SOS or at the end of the buffer. */
            private fun jpegSize(bytes: ByteArray): Pair<Int, Int>? {
                var i = 2
                while (i + 9 < bytes.size) {
                    if (bytes[i] != 0xFF.toByte()) return null
                    val marker = bytes[i + 1].toInt() and 0xFF
                    if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
                    if (marker == 0xD9 || marker == 0xDA) return null // end of image or start of scan
                    val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                    if (length < 2 || i + 2 + length > bytes.size) return null
                    val isFrame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
                    if (isFrame) {
                        if (length < 7) return null
                        val height = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                        val width = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                        if (width <= 0 || height <= 0) return null
                        return width to height
                    }
                    i += 2 + length
                }
                return null
            }

            private fun readInt(bytes: ByteArray, at: Int): Int =
                ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
        }
    }

    sealed interface Block {
        data class Heading(val level: Int, val runs: List<Run>) : Block {
            init {
                require(level in 1..6) { "heading level $level outside 1..6" }
                require(runs.isNotEmpty()) { "heading runs must not be empty" }
            }
        }

        data class Paragraph(val runs: List<Run>) : Block

        data class ListItem(val ordered: Boolean, val level: Int = 0, val runs: List<Run>) : Block {
            init {
                require(level in 0..8) { "list level $level outside 0..8" }
            }
        }

        data object PageBreak : Block

        /** A block-level inline picture. Alt text is mandatory: a document that cannot describe an
         *  image must not silently omit the description. */
        data class Picture(
            val image: Image,
            val alt: String,
            val widthEmu: Int = image.widthEmu,
            val heightEmu: Int = image.heightEmu,
        ) : Block {
            init {
                require(alt.length <= 1_024) { "alt text exceeds 1024 characters" }
                require(widthEmu in 1..MAX_IMAGE_EMU && heightEmu in 1..MAX_IMAGE_EMU) {
                    "picture extent ${widthEmu}x$heightEmu EMU outside 1..$MAX_IMAGE_EMU"
                }
            }
        }

        data class BlockQuote(val runs: List<Run>) : Block

        data class CodeBlock(val lines: List<String>) : Block {
            init {
                require(lines.isNotEmpty()) { "code block must contain at least 1 line" }
                lines.forEach(::validateXmlChars)
            }
        }

        data class Table(val rows: List<List<String>>) : Block {
            init {
                require(rows.isNotEmpty() && rows[0].isNotEmpty()) { "table must be non-empty" }
                val cols = rows[0].size
                require(cols <= MAX_TABLE_COLS) { "table columns $cols exceed $MAX_TABLE_COLS" }
                require(rows.all { it.size == cols }) { "table rows must be rectangular ($cols columns)" }
                rows.flatten().forEach(::validateXmlChars)
            }
        }
    }

    data class Document(val title: String = "", val blocks: List<Block>) {
        init {
            validateXmlChars(title)
            require(blocks.size <= MAX_BLOCKS) { "document blocks ${blocks.size} exceed $MAX_BLOCKS" }
        }
    }

    /** Existing callers retain the original single numbered-instance bytes. */
    fun write(doc: Document): ByteArray = write(doc, restartOrderedGroups = false)

    /**
     * Optional level-zero-only grouping for Writer-generated lists. Each later contiguous
     * ordered group gets a new numId and a level-zero start override of one. This encodes
     * a request, not a guarantee that every document consumer renders it identically.
     */
    fun write(doc: Document, restartOrderedGroups: Boolean): ByteArray {
        var groups = 0
        var previousOrdered = false
        if (restartOrderedGroups) for (block in doc.blocks) {
            val ordered = block is Block.ListItem && block.ordered
            if (ordered) {
                require((block as Block.ListItem).level == 0) { "Restart groups support only level-zero ordered lists" }
                if (!previousOrdered) groups++
            }
            previousOrdered = ordered
        }
        val hyperlinks = LinkedHashSet<String>()
        for (block in doc.blocks) {
            for (run in runsOf(block)) {
                val href = run.href ?: continue
                hyperlinks.add(href)
                require(hyperlinks.size <= MAX_HYPERLINKS) { "document exceeds $MAX_HYPERLINKS hyperlinks" }
            }
        }
        val hrefIds = hyperlinks.withIndex().associate { (index, href) -> href to "rId${index + 3}" }
        val images = LinkedHashSet<Image>()
        for (block in doc.blocks) {
            if (block !is Block.Picture) continue
            if (!images.contains(block.image)) {
                require(images.size < MAX_IMAGES) { "document exceeds $MAX_IMAGES distinct images" }
                images.add(block.image)
            }
        }
        val imageParts = LinkedHashMap<Image, String>()
        val imageIds = LinkedHashMap<Image, String>()
        images.forEachIndexed { index, image ->
            imageParts[image] = "media/image${index + 1}.${image.format.extension}"
            imageIds[image] = "rId${3 + hrefIds.size + index}"
        }
        val parts = linkedMapOf(
            "[Content_Types].xml" to contentTypesXml(images.map { it.format }).toByteArray(StandardCharsets.UTF_8),
            "_rels/.rels" to rootRelsXml().toByteArray(StandardCharsets.UTF_8),
            "word/_rels/document.xml.rels" to documentRelsXml(hrefIds, imageParts, imageIds).toByteArray(StandardCharsets.UTF_8),
            "word/styles.xml" to stylesXml().toByteArray(StandardCharsets.UTF_8),
            "word/numbering.xml" to defaultNumberingXml(groups).toByteArray(StandardCharsets.UTF_8),
            "word/document.xml" to buildDocumentXml(doc, restartOrderedGroups, hrefIds, imageParts, imageIds)
                .toByteArray(StandardCharsets.UTF_8),
        )
        for ((image, target) in imageParts) parts["word/$target"] = image.bytes
        return storedZipBytes(parts)
    }

    fun read(docxBytes: ByteArray): Document {
        require(docxBytes.size <= MAX_ZIP_BYTES) { "docx exceeds $MAX_ZIP_BYTES bytes" }
        val parts = readZipParts(docxBytes)
        require(parts.keys.none { it.endsWith("vbaProject.bin", ignoreCase = true) }) {
            "VBA macro part (vbaProject.bin) refused"
        }
        val docBytes = parts["word/document.xml"]
            ?: throw IllegalArgumentException("missing word/document.xml")
        val numberingDefs = parts["word/numbering.xml"]?.let(DocxNumbering::parseXmlBytes)
        return parseDocumentBlocks(docBytes, numberingDefs, parts["word/_rels/document.xml.rels"], parts)
    }

    private fun buildDocumentXml(
        doc: Document,
        restartOrderedGroups: Boolean,
        hrefIds: Map<String, String> = emptyMap(),
        imageParts: Map<Image, String> = emptyMap(),
        imageIds: Map<Image, String> = emptyMap(),
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        // Relationship-bearing namespaces are declared only when the document actually uses them,
        // so a document without links or pictures stays byte-identical to the pinned legacy writer.
        val relNs = if (hrefIds.isEmpty() && imageParts.isEmpty()) "" else """ xmlns:r="$REL_NS""""
        val drawingNs = if (imageParts.isEmpty()) "" else
            """ xmlns:wp="$WP_NS" xmlns:a="$DRAWING_NS" xmlns:pic="$PICTURE_NS""""
        append("""<w:document xmlns:w="$WORD_NS"$relNs$drawingNs><w:body>""")
        var drawingId = 0
        var orderedGroup = 0
        var previousOrdered = false
        for (b in doc.blocks) {
            val ordered = b is Block.ListItem && b.ordered
            if (ordered && !previousOrdered) orderedGroup++
            previousOrdered = ordered
            when (b) {
                is Block.Heading -> {
                    append("""<w:p><w:pPr><w:pStyle w:val="Heading${b.level}"/></w:pPr>""")
                    appendRuns(b.runs, hrefIds)
                    append("</w:p>")
                }
                is Block.Paragraph -> {
                    append("<w:p>")
                    appendRuns(b.runs, hrefIds)
                    append("</w:p>")
                }
                is Block.ListItem -> {
                    val numId = if (!b.ordered) BULLET_NUM_ID else if (restartOrderedGroups) ORDERED_NUM_ID + orderedGroup - 1 else ORDERED_NUM_ID
                    append("""<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="${b.level}"/><w:numId w:val="$numId"/></w:numPr></w:pPr>""")
                    appendRuns(b.runs, hrefIds)
                    append("</w:p>")
                }
                is Block.BlockQuote -> {
                    append("""<w:p><w:pPr><w:pStyle w:val="Quote"/></w:pPr>""")
                    appendRuns(b.runs, hrefIds)
                    append("</w:p>")
                }
                is Block.PageBreak -> {
                    append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
                }
                is Block.Picture -> {
                    drawingId++
                    val target = imageParts[b.image] ?: error("picture part missing")
                    val id = imageIds[b.image] ?: error("picture relationship missing")
                    appendPicture(b, drawingId, target, id)
                }
                is Block.CodeBlock -> {
                    append("""<w:p><w:pPr><w:pStyle w:val="CodeBlock"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii="monospace" w:hAnsi="monospace"/></w:rPr>""")
                    for ((idx, line) in b.lines.withIndex()) {
                        if (idx > 0) append("<w:br/>")
                        appendTextContent(line)
                    }
                    append("</w:r></w:p>")
                }
                is Block.Table -> {
                    append("<w:tbl><w:tblPr/><w:tblGrid>")
                    repeat(b.rows[0].size) { append("<w:gridCol/>") }
                    append("</w:tblGrid>")
                    for (row in b.rows) {
                        append("<w:tr>")
                        for (cell in row) {
                            append("<w:tc><w:p><w:r>")
                            appendTextContent(cell)
                            append("</w:r></w:p></w:tc>")
                        }
                        append("</w:tr>")
                    }
                    append("</w:tbl>")
                }
            }
        }
        append("<w:sectPr/></w:body></w:document>")
    }

    /**
     * Emits one inline picture. The shape is the minimal DrawingML that Word, LibreOffice and
     * python-docx all agree on: wp:inline -> wp:extent + wp:docPr + a:graphic/pic:pic.
     */
    private fun StringBuilder.appendPicture(block: Block.Picture, index: Int, target: String, relId: String) {
        val name = target.substringAfterLast('/')
        val alt = escapeXml(block.alt)
        append("""<w:p><w:r><w:drawing>""")
        append("""<wp:inline distT="0" distB="0" distL="0" distR="0">""")
        append("""<wp:extent cx="${block.widthEmu}" cy="${block.heightEmu}"/>""")
        append("""<wp:effectExtent l="0" t="0" r="0" b="0"/>""")
        append("""<wp:docPr id="$index" name="Picture $index" descr="$alt"/>""")
        append("""<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>""")
        append("""<a:graphic><a:graphicData uri="$PICTURE_URI"><pic:pic>""")
        append("""<pic:nvPicPr><pic:cNvPr id="$index" name="$name" descr="$alt"/><pic:cNvPicPr/></pic:nvPicPr>""")
        append("""<pic:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""")
        append("""<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${block.widthEmu}" cy="${block.heightEmu}"/></a:xfrm>""")
        append("""<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""")
        append("""</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
    }

    private fun StringBuilder.appendRuns(runs: List<Run>, hrefIds: Map<String, String> = emptyMap()) {
        var index = 0
        while (index < runs.size) {
            val run = runs[index]
            val href = run.href
            if (href != null) {
                var end = index
                while (end < runs.size && runs[end].href == href) end++
                val id = hrefIds[href] ?: error("hyperlink relationship missing for $href")
                append("""<w:hyperlink r:id="$id" w:history="1">""")
                appendPlainRuns(runs.subList(index, end))
                append("</w:hyperlink>")
                index = end
            } else {
                appendPlainRuns(listOf(run))
                index++
            }
        }
    }

    private fun StringBuilder.appendPlainRuns(runs: List<Run>) {
        for (r in runs) {
            if (r.text.isEmpty()) continue
            append("<w:r>")
            if (r.bold || r.italic || r.underline || r.strike || r.code) {
                append("<w:rPr>")
                if (r.bold) append("<w:b/>")
                if (r.italic) append("<w:i/>")
                if (r.underline) append("""<w:u w:val="single"/>""")
                if (r.strike) append("<w:strike/>")
                if (r.code) append("""<w:rFonts w:ascii="monospace" w:hAnsi="monospace"/>""")
                append("</w:rPr>")
            }
            appendTextContent(r.text)
            append("</w:r>")
        }
    }

    /** WordprocessingML represents tabs and soft line breaks as elements, not literal w:t whitespace. */
    private fun StringBuilder.appendTextContent(text: String) {
        var start = 0
        for (index in text.indices) {
            val character = text[index]
            if (character != '\t' && character != '\n') continue
            if (index > start) append("""<w:t xml:space="preserve">${escapeXml(text.substring(start, index))}</w:t>""")
            append(if (character == '\t') "<w:tab/>" else "<w:br/>")
            start = index + 1
        }
        if (start < text.length) append("""<w:t xml:space="preserve">${escapeXml(text.substring(start))}</w:t>""")
    }

    private fun parseDocumentBlocks(
        docBytes: ByteArray,
        numberingDefs: DocxNumbering.Definitions?,
        relsBytes: ByteArray? = null,
        parts: Map<String, ByteArray> = emptyMap(),
    ): Document {
        val root = BoundedXml.parse(docBytes)
        require(root.name.local == "document") { "expected <w:document>, got <${root.name.local}>" }
        val body = root.children.firstOrNull { it.name.local == "body" }
            ?: throw IllegalArgumentException("missing <w:body>")
        val blocks = ArrayList<Block>()
        val linkTargets = parseLinkTargets(relsBytes)
        val imageTargets = parseImageTargets(relsBytes)
        var pictures = 0

        for (node in body.children) {
            when (node.name.local) {
                "p" -> {
                    require(blocks.size < MAX_BLOCKS) { "document exceeds $MAX_BLOCKS blocks" }
                    val picture = parsePicture(node, imageTargets, parts)
                    if (picture != null) {
                        require(++pictures <= MAX_IMAGES) { "document exceeds $MAX_IMAGES pictures" }
                        blocks.add(picture)
                    } else {
                        blocks.add(parseParagraph(node, numberingDefs, linkTargets))
                    }
                }
                "tbl" -> {
                    require(blocks.size < MAX_BLOCKS) { "document exceeds $MAX_BLOCKS blocks" }
                    parseTable(node)?.let(blocks::add)
                }
            }
        }
        return Document(blocks = blocks)
    }

    private fun parseImageTargets(relsBytes: ByteArray?): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val root = BoundedXml.parse(relsBytes)
        val targets = LinkedHashMap<String, String>()
        for (node in root.children) {
            if (node.name.local != "Relationship") continue
            val type = node.attr("Type", "") ?: continue
            if (!type.endsWith("/image")) continue
            val id = node.attr("Id", "") ?: continue
            val target = node.attr("Target", "") ?: continue
            targets[id] = target
        }
        return targets
    }

    /** One picture per paragraph. A paragraph mixing text and a drawing keeps the text fallback. */
    private fun parsePicture(pNode: BoundedXml.Node, imageTargets: Map<String, String>,
                             parts: Map<String, ByteArray>): Block.Picture? {
        for (child in pNode.children) {
            if (child.name.local == "pPr") continue
            if (child.name.local != "r") return null // text anywhere in the paragraph wins
            val drawing = child.children.firstOrNull { it.name.local == "drawing" } ?: return null
            val inline = drawing.children.firstOrNull { it.name.local == "inline" }
                ?: throw IllegalArgumentException("floating pictures (wp:anchor) are not supported")
            val extent = inline.children.firstOrNull { it.name.local == "extent" && it.name.ns == WP_NS }
                ?: throw IllegalArgumentException("picture is missing wp:extent")
            // cx/cy and descr are unqualified attributes in WordprocessingML; accept the namespaced
            // spelling too so a package produced by another writer still reads.
            val cx = (extent.attr("cx") ?: extent.attr("cx", WP_NS))?.toIntOrNull()
                ?: throw IllegalArgumentException("wp:extent cx")
            val cy = (extent.attr("cy") ?: extent.attr("cy", WP_NS))?.toIntOrNull()
                ?: throw IllegalArgumentException("wp:extent cy")
            val docPr = inline.children.firstOrNull { it.name.local == "docPr" }
            val alt = docPr?.attr("descr") ?: docPr?.attr("descr", WP_NS) ?: ""
            val blip = findFirst(inline) { it.name.local == "blip" }
                ?: throw IllegalArgumentException("picture is missing a:blip")
            val relId = blip.attr("embed", REL_NS)
                ?: throw IllegalArgumentException("picture is missing r:embed")
            val target = imageTargets[relId]
                ?: throw IllegalArgumentException("picture relationship $relId is not declared")
            require(!target.startsWith("/") && ".." !in target.split('/') && ':' !in target) {
                "picture target escapes the package: $target"
            }
            val partName = if (target.startsWith("word/")) target else "word/$target"
            val data = parts[partName]
                ?: throw IllegalArgumentException("picture part $partName is missing")
            require(data.size <= MAX_IMAGE_BYTES) { "picture exceeds $MAX_IMAGE_BYTES bytes" }
            val image = Image.of(data)
            val declared = partName.substringAfterLast('.').lowercase()
            require(declared == image.format.extension) {
                "picture part $partName does not match its ${image.format.extension} magic bytes"
            }
            return Block.Picture(image, alt, cx, cy)
        }
        return null
    }

    private fun findFirst(node: BoundedXml.Node, predicate: (BoundedXml.Node) -> Boolean): BoundedXml.Node? {
        if (predicate(node)) return node
        for (child in node.children) findFirst(child, predicate)?.let { return it }
        return null
    }

    private fun parseLinkTargets(relsBytes: ByteArray?): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val root = BoundedXml.parse(relsBytes)
        val targets = LinkedHashMap<String, String>()
        for (node in root.children) {
            if (node.name.local != "Relationship") continue
            val type = node.attr("Type", "") ?: continue
            if (!type.endsWith("/hyperlink")) continue
            val id = node.attr("Id", "") ?: continue
            val target = node.attr("Target", "") ?: continue
            if (targets.size >= MAX_HYPERLINKS) break
            targets[id] = target
        }
        return targets
    }

    private fun hasPageBreak(pNode: BoundedXml.Node): Boolean =
        pNode.children.any { child ->
            child.name.local == "r" && child.children.any { runChild ->
                runChild.name.local == "br" && (wAttr(runChild, "type") ?: "") == "page"
            }
        }

    private fun parseParagraph(pNode: BoundedXml.Node, numberingDefs: DocxNumbering.Definitions?, linkTargets: Map<String, String> = emptyMap()): Block {
        var pStyle: String? = null
        var numId: Int? = null
        var ilvl = 0
        val runs = ArrayList<Run>()

        for (child in pNode.children) {
            when (child.name.local) {
                "pPr" -> {
                    for (pr in child.children) {
                        when (pr.name.local) {
                            "pStyle" -> pStyle = wAttr(pr, "val")
                            "numPr" -> {
                                for (np in pr.children) {
                                    when (np.name.local) {
                                        "ilvl" -> ilvl = (wAttr(np, "val")?.toIntOrNull() ?: 0).coerceIn(0, 8)
                                        "numId" -> numId = wAttr(np, "val")?.toIntOrNull()
                                    }
                                }
                            }
                        }
                    }
                }
                "r" -> parseRun(child)?.let(runs::add)
                "hyperlink" -> {
                    val id = child.attr("id", REL_NS)
                    val target = id?.let { linkTargets[it] }
                    for (inner in child.children) {
                        if (inner.name.local != "r") continue
                        val run = parseRun(inner) ?: continue
                        runs.add(if (target == null) run else run.copy(href = target))
                    }
                }
            }
        }

        val style = pStyle ?: ""
        val headingLvl = style.removePrefix("Heading").toIntOrNull()
        val textOnly = runs.all { it.text.isBlank() }
        return when {
            textOnly && pStyle == null && numId == null && hasPageBreak(pNode) -> Block.PageBreak
            headingLvl != null && headingLvl in 1..6 -> Block.Heading(headingLvl, runs.ifEmpty { listOf(Run("")) })
            style == "CodeBlock" -> Block.CodeBlock(runs.joinToString("") { it.text }.split('\n'))
            style == "Quote" || style == "IntenseQuote" -> Block.BlockQuote(runs)
            numId != null && numId != 0 -> Block.ListItem(isOrderedNum(numberingDefs, numId!!, ilvl), ilvl, runs)
            else -> Block.Paragraph(runs)
        }
    }

    private fun parseRun(rNode: BoundedXml.Node): Run? {
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var code = false
        val sb = StringBuilder()

        for (child in rNode.children) {
            when (child.name.local) {
                "rPr" -> {
                    for (pr in child.children) {
                        when (pr.name.local) {
                            "b" -> bold = wBoolAttr(pr)
                            "i" -> italic = wBoolAttr(pr)
                            "u" -> underline = (wAttr(pr, "val") ?: "single") != "none"
                            "strike" -> strike = wBoolAttr(pr)
                            "rFonts" -> {
                                val ascii = wAttr(pr, "ascii")?.lowercase() ?: ""
                                if (ascii == "monospace" || "courier" in ascii || "consolas" in ascii) code = true
                            }
                        }
                    }
                }
                "t" -> sb.append(child.text)
                "br" -> sb.append('\n')
                "tab" -> sb.append('\t')
            }
        }
        return if (sb.isEmpty()) null else Run(sb.toString(), bold, italic, underline, strike, code)
    }

    private fun parseTable(tblNode: BoundedXml.Node): Block.Table? {
        val rows = ArrayList<List<String>>()
        for (tr in tblNode.children) {
            if (tr.name.local != "tr") continue
            val cells = ArrayList<String>()
            for (tc in tr.children) {
                if (tc.name.local != "tc") continue
                val pTexts = ArrayList<String>()
                for (p in tc.children) {
                    if (p.name.local != "p") continue
                    val pSb = StringBuilder()
                    for (r in p.children) {
                        if (r.name.local == "r") {
                            for (rc in r.children) {
                                when (rc.name.local) {
                                    "t" -> pSb.append(rc.text)
                                    "br" -> pSb.append('\n')
                                    "tab" -> pSb.append('\t')
                                }
                            }
                        }
                    }
                    pTexts.add(pSb.toString())
                }
                cells.add(pTexts.joinToString("\n"))
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }
        if (rows.isEmpty()) return null
        val width = rows.maxOf { it.size }
        val normalized = rows.map { r -> if (r.size == width) r else r + List(width - r.size) { "" } }
        return Block.Table(normalized)
    }

    private fun wAttr(node: BoundedXml.Node, local: String): String? =
        node.attr(local, WORD_NS) ?: node.attr(local, "")

    private fun wBoolAttr(node: BoundedXml.Node): Boolean {
        val v = wAttr(node, "val") ?: return true
        return v != "0" && v != "false"
    }

    private fun isOrderedNum(defs: DocxNumbering.Definitions?, numId: Int, ilvl: Int): Boolean {
        if (defs != null && numId in defs.nums) {
            val fmt = defs.resolveLevel(numId, ilvl).numFmt
            return fmt != DocxNumbering.NumFormat.BULLET && fmt != DocxNumbering.NumFormat.NONE
        }
        return numId != BULLET_NUM_ID
    }

    private fun readZipParts(zipBytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val names = HashSet<String>()
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
                require(names.add(name)) { "duplicate ZIP entry '$name'" }
                val payload = if (entry.isDirectory) null else ByteArrayOutputStream()
                var partBytes = 0L
                while (true) {
                    // A directory entry may still carry compressed data, and
                    // closeEntry() discards the rest without passing it through
                    // these counters. Read every entry through the same budget.
                    val want = minOf(buf.size.toLong(), MAX_PART_BYTES - partBytes + 1L,
                        MAX_ZIP_BYTES - totalExpanded + 1L).toInt()
                    val n = zis.read(buf, 0, want)
                    if (n < 0) break
                    require(n > 0) { "ZIP decoder made no progress" }
                    partBytes += n
                    totalExpanded += n
                    require(partBytes <= MAX_PART_BYTES && totalExpanded <= MAX_ZIP_BYTES) {
                        "expanded ZIP budget exceeded on '$name'"
                    }
                    payload?.write(buf, 0, n)
                }
                if (payload != null) out[name] = payload.toByteArray()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return out
    }

    private fun storedZip(entries: Map<String, String>): ByteArray =
        storedZipBytes(entries.mapValues { (_, content) -> content.toByteArray(StandardCharsets.UTF_8) })

    private fun storedZipBytes(entries: Map<String, ByteArray>): ByteArray {
        val body = ByteArrayOutputStream()
        val cd = ByteArrayOutputStream()
        var count = 0
        for ((name, data) in entries) {
            val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(data) }.value.toInt()
            val offset = body.size()
            body.write(leHeader(30) {
                putInt(0x04034b50); putShort(20); putShort(0x0800); putShort(0)
                putShort(0); putShort(0x0021); putInt(crc)
                putInt(data.size); putInt(data.size); putShort(nameBytes.size.toShort()); putShort(0)
            })
            body.write(nameBytes)
            body.write(data)
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

    private fun contentTypesXml(imageFormats: List<ImageFormat> = emptyList()): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            imageFormats.distinct().joinToString("") { format ->
                """<Default Extension="${format.extension}" ContentType="${format.contentType}"/>"""
            } +
            """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""" +
            """<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>""" +
            """</Types>"""

    private fun rootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
            """</Relationships>"""

    private fun documentRelsXml(
        hrefIds: Map<String, String> = emptyMap(),
        imageParts: Map<Image, String> = emptyMap(),
        imageIds: Map<Image, String> = emptyMap(),
    ): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""" +
            hrefIds.entries.joinToString("") { (href, id) ->
                """<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${escapeXml(href)}" TargetMode="External"/>"""
            } +
            imageParts.entries.joinToString("") { (image, target) ->
                val id = imageIds[image] ?: error("picture relationship missing")
                """<Relationship Id="$id" Type="$IMAGE_REL_TYPE" Target="${escapeXml(target)}"/>"""
            } +
            """</Relationships>"""

    private fun stylesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:styles xmlns:w="$WORD_NS">""")
        append("""<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>""")
        val halfPointSizes = listOf(32, 28, 26, 24, 22, 20)
        for (lvl in 1..6) {
            val size = halfPointSizes[lvl - 1]
            append("""<w:style w:type="paragraph" w:styleId="Heading$lvl"><w:name w:val="heading $lvl"/>""")
            append("""<w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>""")
            append("""<w:pPr><w:keepNext/><w:outlineLvl w:val="${lvl - 1}"/></w:pPr>""")
            append("""<w:rPr><w:b/><w:bCs/><w:sz w:val="$size"/><w:szCs w:val="$size"/></w:rPr></w:style>""")
        }
        append("""<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/></w:style>""")
        append("""<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:pPr><w:ind w:left="720"/></w:pPr><w:rPr><w:i/></w:rPr></w:style>""")
        append("""<w:style w:type="paragraph" w:styleId="CodeBlock"><w:name w:val="Code Block"/><w:basedOn w:val="Normal"/></w:style>""")
        append("""</w:styles>""")
    }

    private fun defaultNumberingXml(orderedGroups: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:numbering xmlns:w="$WORD_NS">""")
        append("""<w:abstractNum w:abstractNumId="0">""")
        for (lvl in 0..8) {
            append("""<w:lvl w:ilvl="$lvl"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#x2022;"/></w:lvl>""")
        }
        append("""</w:abstractNum>""")
        append("""<w:abstractNum w:abstractNumId="1">""")
        for (lvl in 0..8) {
            val pat = (1..(lvl + 1)).joinToString(".") { "%$it" } + "."
            append("""<w:lvl w:ilvl="$lvl"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="$pat"/><w:lvlRestart w:val="$lvl"/></w:lvl>""")
        }
        append("""</w:abstractNum>""")
        append("""<w:num w:numId="$BULLET_NUM_ID"><w:abstractNumId w:val="0"/></w:num>""")
        append("""<w:num w:numId="$ORDERED_NUM_ID"><w:abstractNumId w:val="1"/></w:num>""")
        if (orderedGroups > 1) for (group in 2..orderedGroups) {
            val numId = ORDERED_NUM_ID + group - 1
            append("""<w:num w:numId="$numId"><w:abstractNumId w:val="1"/><w:lvlOverride w:ilvl="0"><w:startOverride w:val="1"/></w:lvlOverride></w:num>""")
        }
        append("""</w:numbering>""")
    }

    private fun validateXmlChars(s: String) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate()) {
                require(i + 1 < s.length && s[i + 1].isLowSurrogate()) { "unpaired high surrogate" }
                i += 2
                continue
            }
            require(!c.isLowSurrogate()) { "unpaired low surrogate" }
            val cp = c.code
            val ok = cp == 0x09 || cp == 0x0A || cp == 0x0D || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD
            require(ok) { "XML 1.0 illegal control char U+${cp.toString(16).uppercase()}" }
            i++
        }
    }

    private fun validateHref(href: String) {
        require(href.length <= MAX_HREF_CHARS) { "hyperlink target exceeds $MAX_HREF_CHARS characters" }
        require(href.none { it.code < 0x20 || it.code == 0x7F }) { "hyperlink target contains a control character" }
        val lower = href.lowercase()
        require(lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
            "hyperlink scheme must be http, https or mailto"
        }
    }

    private fun runsOf(block: Block): List<Run> = when (block) {
        is Block.Heading -> block.runs
        is Block.Paragraph -> block.runs
        is Block.ListItem -> block.runs
        is Block.BlockQuote -> block.runs
        is Block.CodeBlock, is Block.Table, is Block.Picture, Block.PageBreak -> emptyList()
    }

    private fun escapeXml(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            '\r' -> append("&#13;") // XML parsers otherwise normalize a literal CR to LF.
            else -> append(c)
        }
    }
}
