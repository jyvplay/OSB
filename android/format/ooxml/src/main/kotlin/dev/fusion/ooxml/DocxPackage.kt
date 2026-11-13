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
    const val MAX_HEADER_FOOTER_CHARS = 1_024
    const val A4_WIDTH_TWIPS = 11_906
    const val A4_HEIGHT_TWIPS = 16_838
    const val MIN_PAGE_TWIPS = 5_760
    const val MAX_PAGE_TWIPS = 31_680
    const val MIN_CONTENT_TWIPS = 720
    private const val HEADER_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/header"
    private const val FOOTER_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer"
    private const val HEADER_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"
    private const val FOOTER_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"
    private const val HEADER_PART = "word/header1.xml"
    private const val FOOTER_PART = "word/footer1.xml"
    private const val HEADER_FIRST_PART = "word/header2.xml"
    private const val FOOTER_FIRST_PART = "word/footer2.xml"
    private const val HEADER_EVEN_PART = "word/header3.xml"
    private const val FOOTER_EVEN_PART = "word/footer3.xml"
    private const val SETTINGS_PART = "word/settings.xml"
    private const val SETTINGS_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings"
    private const val SETTINGS_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"
    private const val SETTINGS_REL_ID = "rIdSet1"
    private const val HEADER_REL_ID = "rIdHdr1"
    private const val FOOTER_REL_ID = "rIdFtr1"
    private const val HEADER_FIRST_REL_ID = "rIdHdr2"
    private const val FOOTER_FIRST_REL_ID = "rIdFtr2"
    private const val HEADER_EVEN_REL_ID = "rIdHdr3"
    private const val FOOTER_EVEN_REL_ID = "rIdFtr3"
    /** Word defines at most 45 newspaper columns; column spacing is a twip measure. */
    private const val MAX_COLUMNS = 45
    private const val MAX_PAGE_NUMBER_START = 1_000_000
    private const val MAX_LINE_COUNT_BY = 1_000
    private const val MAX_LINE_START = 1_000_000
    /** Word's ST_TblWidth window is 0..31680 twips; a column below 240 twips is unusable. */
    private const val MIN_TABLE_COL_TWIPS = 240
    private const val MAX_TABLE_COL_TWIPS = 31_680
    private const val MAX_TABLE_TOTAL_TWIPS = 31_680
    private const val DEFAULT_COLUMN_SPACE_TWIPS = 720
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

        data class Table(
            val rows: List<List<String>>,
            val columnWidthsTwips: List<Int>? = null,
            val repeatHeaderRow: Boolean = false,
        ) : Block {
            init {
                require(rows.isNotEmpty() && rows[0].isNotEmpty()) { "table must be non-empty" }
                val cols = rows[0].size
                require(cols <= MAX_TABLE_COLS) { "table columns $cols exceed $MAX_TABLE_COLS" }
                require(rows.all { it.size == cols }) { "table rows must be rectangular ($cols columns)" }
                rows.flatten().forEach(::validateXmlChars)
                columnWidthsTwips?.let { widths ->
                    require(widths.size == cols) {
                        "table column widths ${widths.size} do not match $cols columns"
                    }
                    widths.forEach { width ->
                        require(width in MIN_TABLE_COL_TWIPS..MAX_TABLE_COL_TWIPS) {
                            "table column width $width outside $MIN_TABLE_COL_TWIPS..$MAX_TABLE_COL_TWIPS twips"
                        }
                    }
                    val total = widths.sum()
                    require(total <= MAX_TABLE_TOTAL_TWIPS) {
                        "table width $total exceeds $MAX_TABLE_TOTAL_TWIPS twips"
                    }
                }
            }
        }
    }

    /** Page geometry in twentieths of a point (twips), the unit WordprocessingML requires. */
    data class PageSetup(
        val widthTwips: Int = A4_WIDTH_TWIPS,
        val heightTwips: Int = A4_HEIGHT_TWIPS,
        val orientation: Orientation = Orientation.PORTRAIT,
        val marginTopTwips: Int = 1_440,
        val marginRightTwips: Int = 1_440,
        val marginBottomTwips: Int = 1_440,
        val marginLeftTwips: Int = 1_440,
        val headerTwips: Int = 708,
        val footerTwips: Int = 708,
    ) {
        enum class Orientation { PORTRAIT, LANDSCAPE }

        init {
            require(widthTwips in MIN_PAGE_TWIPS..MAX_PAGE_TWIPS) { "page width $widthTwips outside $MIN_PAGE_TWIPS..$MAX_PAGE_TWIPS twips" }
            require(heightTwips in MIN_PAGE_TWIPS..MAX_PAGE_TWIPS) { "page height $heightTwips outside $MIN_PAGE_TWIPS..$MAX_PAGE_TWIPS twips" }
            listOf(marginTopTwips, marginRightTwips, marginBottomTwips, marginLeftTwips).forEach {
                require(it in 0..MAX_PAGE_TWIPS) { "margin $it outside 0..$MAX_PAGE_TWIPS twips" }
            }
            require(headerTwips in 0..MAX_PAGE_TWIPS && footerTwips in 0..MAX_PAGE_TWIPS) {
                "header/footer distance outside 0..$MAX_PAGE_TWIPS twips"
            }
            require(widthTwips - marginLeftTwips - marginRightTwips >= MIN_CONTENT_TWIPS) {
                "margins leave less than $MIN_CONTENT_TWIPS twips of text width"
            }
            require(heightTwips - marginTopTwips - marginBottomTwips >= MIN_CONTENT_TWIPS) {
                "margins leave less than $MIN_CONTENT_TWIPS twips of text height"
            }
            require(
                (orientation == Orientation.LANDSCAPE && widthTwips > heightTwips) ||
                    (orientation == Orientation.PORTRAIT && heightTwips >= widthTwips),
            ) { "orientation $orientation contradicts ${widthTwips}x$heightTwips twips" }
        }

        fun landscape(): PageSetup = copy(
            widthTwips = maxOf(widthTwips, heightTwips),
            heightTwips = minOf(widthTwips, heightTwips),
            orientation = Orientation.LANDSCAPE,
        )

        fun portrait(): PageSetup = copy(
            widthTwips = minOf(widthTwips, heightTwips),
            heightTwips = maxOf(widthTwips, heightTwips),
            orientation = Orientation.PORTRAIT,
        )

        companion object {
            fun a4(): PageSetup = PageSetup()

            fun letter(): PageSetup = PageSetup(widthTwips = 12_240, heightTwips = 15_840)

            fun withMargins(
                page: PageSetup = a4(),
                top: Int = 1_440,
                right: Int = 1_440,
                bottom: Int = 1_440,
                left: Int = 1_440,
            ): PageSetup = page.copy(
                marginTopTwips = top, marginRightTwips = right,
                marginBottomTwips = bottom, marginLeftTwips = left,
            )
        }
    }

    /**
     * Section layout: one page geometry plus an optional running header and footer.
     *
     * Header and footer text may carry the field tokens `{PAGE}` and `{NUMPAGES}`; each becomes a
     * `w:fldSimple` whose cached result is `1`, which is what a consumer shows before it
     * recalculates. This writer never evaluates page numbers itself: pagination belongs to the
     * consumer, and pretending otherwise would be a false claim.
     */
    data class Layout(
        val pageSetup: PageSetup = PageSetup(),
        val header: String? = null,
        val footer: String? = null,
        val columns: Int = 1,
        val columnSpaceTwips: Int = DEFAULT_COLUMN_SPACE_TWIPS,
        val pageNumberStart: Int? = null,
        val sectionType: SectionType = SectionType.NEXT_PAGE,
        /**
         * Different first page: `w:titlePg` plus `w:headerReference`/`w:footerReference` of type
         * `first`. A section that supplies either one therefore carries a first-page part; a
         * section that supplies neither emits neither, so every earlier layout keeps its bytes.
         */
        val firstPageHeader: String? = null,
        val firstPageFooter: String? = null,
        /**
         * Even-page header/footer. Word only honours these when the document settings carry
         * `w:evenAndOddHeaders`, so declaring either one also writes `word/settings.xml` with that
         * element, its relationship and its content-type override. A layout that declares neither
         * writes no settings part, keeping every earlier package byte-identical.
         */
        val evenPageHeader: String? = null,
        val evenPageFooter: String? = null,
        /** `w:lnNumType`: line numbering for this section. Absent means no line numbers. */
        val lineNumbering: LineNumbering? = null,
        /** `w:vAlign`: vertical alignment of the text body. Word's default is `top`, so `TOP`
         *  emits nothing and every earlier layout keeps its bytes. */
        val verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
    ) {
        /** `w:lnNumType` (`w:countBy`, `w:start`, `w:restart`). */
        data class LineNumbering(
            val countBy: Int = 1,
            val start: Int = 1,
            val restart: Restart = Restart.NEW_PAGE,
        ) {
            enum class Restart(val value: String) {
                NEW_PAGE("newPage"),
                CONTINUOUS("continuous"),
                NEW_SECTION("newSection"),
            }

            init {
                require(countBy in 1..MAX_LINE_COUNT_BY) { "line countBy $countBy outside 1..$MAX_LINE_COUNT_BY" }
                require(start in 1..MAX_LINE_START) { "line start $start outside 1..$MAX_LINE_START" }
            }
        }

        /** `w:vAlign` values defined by ECMA-376 section 17.6.22. */
        enum class VerticalAlignment(val value: String) {
            TOP("top"),
            CENTER("center"),
            BOTH("both"),
            BOTTOM("bottom"),
        }
        /**
         * `w:type` of the section. `nextPage` is Word's default and is therefore omitted from the
         * XML, so a layout that asks for nothing else keeps emitting the same bytes as before.
         */
        enum class SectionType(val value: String) {
            NEXT_PAGE("nextPage"),
            CONTINUOUS("continuous"),
            EVEN_PAGE("evenPage"),
            ODD_PAGE("oddPage"),
        }

        init {
            require(columns in 1..MAX_COLUMNS) { "columns $columns outside 1..$MAX_COLUMNS" }
            require(columnSpaceTwips in 0..MAX_PAGE_TWIPS) {
                "column spacing $columnSpaceTwips outside 0..$MAX_PAGE_TWIPS twips"
            }
            require(pageNumberStart == null || pageNumberStart in 1..MAX_PAGE_NUMBER_START) {
                "page number start $pageNumberStart outside 1..$MAX_PAGE_NUMBER_START"
            }
            require(pageNumberStart == null || sectionType != SectionType.CONTINUOUS) {
                "a continuous section cannot restart page numbering"
            }
            for ((name, text) in listOf(
                "header" to header,
                "footer" to footer,
                "first-page header" to firstPageHeader,
                "first-page footer" to firstPageFooter,
                "even-page header" to evenPageHeader,
                "even-page footer" to evenPageFooter,
            )) {
                if (text == null) continue
                validateXmlChars(text)
                require(text.length <= MAX_HEADER_FOOTER_CHARS) {
                    "$name text ${text.length} exceeds $MAX_HEADER_FOOTER_CHARS characters"
                }
                require(text.isNotBlank()) { "$name text must not be blank; omit it instead" }
                var index = text.indexOf('{')
                while (index >= 0) {
                    require(
                        text.startsWith("{PAGE}", index) || text.startsWith("{NUMPAGES}", index),
                    ) { "unknown field token at character $index; only {PAGE} and {NUMPAGES} are defined" }
                    index = text.indexOf('{', index + 1)
                }
                require('}' !in text.replace("{PAGE}", "").replace("{NUMPAGES}", "")) {
                    "$name text contains an unmatched }"
                }
            }
        }
    }

    data class Document(
        val title: String = "",
        val blocks: List<Block>,
        val layout: Layout? = null,
    ) {
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
        val layout = doc.layout
        val headerRelId = if (layout?.header != null) HEADER_REL_ID else null
        val footerRelId = if (layout?.footer != null) FOOTER_REL_ID else null
        val headerFirstRelId = if (layout?.firstPageHeader != null) HEADER_FIRST_REL_ID else null
        val footerFirstRelId = if (layout?.firstPageFooter != null) FOOTER_FIRST_REL_ID else null
        val headerEvenRelId = if (layout?.evenPageHeader != null) HEADER_EVEN_REL_ID else null
        val footerEvenRelId = if (layout?.evenPageFooter != null) FOOTER_EVEN_REL_ID else null
        val settingsRelId = if (headerEvenRelId != null || footerEvenRelId != null) SETTINGS_REL_ID else null
        val parts = linkedMapOf(
            "[Content_Types].xml" to contentTypesXml(
                images.map { it.format }, headerRelId != null, footerRelId != null,
                headerFirstRelId != null, footerFirstRelId != null,
                headerEvenRelId != null, footerEvenRelId != null, settingsRelId != null,
            ).toByteArray(StandardCharsets.UTF_8),
            "_rels/.rels" to rootRelsXml().toByteArray(StandardCharsets.UTF_8),
            "word/_rels/document.xml.rels" to documentRelsXml(
                hrefIds, imageParts, imageIds = imageIds, headerRelId = headerRelId,
                footerRelId = footerRelId, headerFirstRelId = headerFirstRelId,
                footerFirstRelId = footerFirstRelId,
                headerEvenRelId = headerEvenRelId,
                footerEvenRelId = footerEvenRelId,
                settingsRelId = settingsRelId,
            ).toByteArray(StandardCharsets.UTF_8),
            "word/styles.xml" to stylesXml().toByteArray(StandardCharsets.UTF_8),
            "word/numbering.xml" to defaultNumberingXml(groups).toByteArray(StandardCharsets.UTF_8),
            "word/document.xml" to buildDocumentXml(doc, restartOrderedGroups, hrefIds, imageParts, imageIds)
                .toByteArray(StandardCharsets.UTF_8),
        )
        for ((image, target) in imageParts) parts["word/$target"] = image.bytes
        if (layout?.header != null) parts[HEADER_PART] = headerFooterXml("hdr", layout.header).toByteArray(StandardCharsets.UTF_8)
        if (layout?.footer != null) parts[FOOTER_PART] = headerFooterXml("ftr", layout.footer).toByteArray(StandardCharsets.UTF_8)
        if (layout?.firstPageHeader != null) {
            parts[HEADER_FIRST_PART] = headerFooterXml("hdr", layout.firstPageHeader).toByteArray(StandardCharsets.UTF_8)
        }
        if (layout?.firstPageFooter != null) {
            parts[FOOTER_FIRST_PART] = headerFooterXml("ftr", layout.firstPageFooter).toByteArray(StandardCharsets.UTF_8)
        }
        if (layout?.evenPageHeader != null) {
            parts[HEADER_EVEN_PART] = headerFooterXml("hdr", layout.evenPageHeader).toByteArray(StandardCharsets.UTF_8)
        }
        if (layout?.evenPageFooter != null) {
            parts[FOOTER_EVEN_PART] = headerFooterXml("ftr", layout.evenPageFooter).toByteArray(StandardCharsets.UTF_8)
        }
        if (settingsRelId != null) parts[SETTINGS_PART] = settingsXml().toByteArray(StandardCharsets.UTF_8)
        return storedZipBytes(parts)
    }

    /**
     * Reads back the section layout this writer emits: page geometry plus the running header and
     * footer text with the `{PAGE}`/`{NUMPAGES}` field tokens restored. Returns null for a document
     * whose section properties carry no page geometry (the legacy `<w:sectPr/>`).
     */
    fun readLayout(docxBytes: ByteArray): Layout? {
        require(docxBytes.size <= MAX_ZIP_BYTES) { "docx exceeds $MAX_ZIP_BYTES bytes" }
        val parts = readZipParts(docxBytes)
        val document = parts["word/document.xml"]?.toString(StandardCharsets.UTF_8) ?: return null
        val start = document.lastIndexOf("<w:sectPr")
        if (start < 0) return null
        val block = elementAt(document, start) ?: return null
        val pageSize = attributesOf(block, "w:pgSz") ?: return null
        val width = pageSize["w:w"]?.toIntOrNull() ?: return null
        val height = pageSize["w:h"]?.toIntOrNull() ?: return null
        val margins = attributesOf(block, "w:pgMar") ?: emptyMap()
        val page = PageSetup(
            widthTwips = width,
            heightTwips = height,
            orientation = if (pageSize["w:orient"] == "landscape") {
                PageSetup.Orientation.LANDSCAPE
            } else {
                PageSetup.Orientation.PORTRAIT
            },
            marginTopTwips = margins["w:top"]?.toIntOrNull() ?: 1_440,
            marginRightTwips = margins["w:right"]?.toIntOrNull() ?: 1_440,
            marginBottomTwips = margins["w:bottom"]?.toIntOrNull() ?: 1_440,
            marginLeftTwips = margins["w:left"]?.toIntOrNull() ?: 1_440,
            headerTwips = margins["w:header"]?.toIntOrNull() ?: 708,
            footerTwips = margins["w:footer"]?.toIntOrNull() ?: 708,
        )
        val rels = parts["word/_rels/document.xml.rels"]?.toString(StandardCharsets.UTF_8)
        val typeName = attributesOf(block, "w:type")?.get("w:val")
        val sectionType = when (typeName) {
            null -> Layout.SectionType.NEXT_PAGE
            else -> Layout.SectionType.entries.firstOrNull { it.value == typeName }
                ?: throw IllegalArgumentException("unknown section type '$typeName'")
        }
        val columns = attributesOf(block, "w:cols")
        return Layout(
            page,
            referenceText(block, "w:headerReference", rels, parts),
            referenceText(block, "w:footerReference", rels, parts),
            columns = columns?.get("w:num")?.toIntOrNull() ?: 1,
            columnSpaceTwips = columns?.get("w:space")?.toIntOrNull() ?: DEFAULT_COLUMN_SPACE_TWIPS,
            pageNumberStart = attributesOf(block, "w:pgNumType")?.get("w:start")?.toIntOrNull(),
            sectionType = sectionType,
            firstPageHeader = referenceText(block, "w:headerReference", rels, parts, type = "first"),
            firstPageFooter = referenceText(block, "w:footerReference", rels, parts, type = "first"),
            evenPageHeader = referenceText(block, "w:headerReference", rels, parts, type = "even"),
            evenPageFooter = referenceText(block, "w:footerReference", rels, parts, type = "even"),
            lineNumbering = attributesOf(block, "w:lnNumType")?.let { attributes ->
                Layout.LineNumbering(
                    countBy = attributes["w:countBy"]?.toIntOrNull() ?: 1,
                    start = attributes["w:start"]?.toIntOrNull() ?: 1,
                    restart = Layout.LineNumbering.Restart.entries
                        .firstOrNull { it.value == (attributes["w:restart"] ?: "newPage") }
                        ?: throw IllegalArgumentException("unknown line-number restart '${attributes["w:restart"]}'"),
                )
            },
            verticalAlignment = attributesOf(block, "w:vAlign")?.get("w:val")?.let { value ->
                Layout.VerticalAlignment.entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("unknown vertical alignment '$value'")
            } ?: Layout.VerticalAlignment.TOP,
        )
    }

    private fun elementAt(xml: String, start: Int): String? {
        val close = xml.indexOf('>', start)
        if (close < 0) return null
        if (xml[close - 1] == '/') return xml.substring(start, close + 1)
        val end = xml.indexOf("</w:sectPr>", close)
        if (end < 0) return null
        return xml.substring(start, end)
    }

    private val ATTRIBUTE = Regex("([A-Za-z0-9_]+:[A-Za-z0-9_]+)=\"([^\"]*)\"")

    private fun attributesOf(block: String, tag: String): Map<String, String>? {
        val at = block.indexOf("<$tag")
        if (at < 0) return null
        val close = block.indexOf('>', at)
        if (close < 0) return null
        return ATTRIBUTE.findAll(block.substring(at + tag.length + 1, close))
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    /**
     * Text of the reference of [type] (`default` or `first`). A section may carry one reference of
     * each type, so the lookup filters on `w:type` instead of taking the first match of the tag.
     */
    private fun referenceText(
        sectPr: String,
        tag: String,
        rels: String?,
        parts: Map<String, ByteArray>,
        type: String = "default",
    ): String? {
        val at = sectPr.indexOf("<$tag")
        if (at < 0) return null
        var close = sectPr.indexOf('>', at)
        if (close < 0) return null
        var attributes = ATTRIBUTE.findAll(sectPr.substring(at, close)).associate { it.groupValues[1] to it.groupValues[2] }
        while ((attributes["w:type"] ?: "default") != type) {
            val next = sectPr.indexOf("<$tag", close)
            if (next < 0) return null
            close = sectPr.indexOf('>', next)
            if (close < 0) return null
            attributes = ATTRIBUTE.findAll(sectPr.substring(next, close)).associate { it.groupValues[1] to it.groupValues[2] }
        }
        val relId = attributes["r:id"] ?: return null
        val target = rels?.let {
            Regex("<Relationship Id=\"${Regex.escape(relId)}\"" + "[^>]*Target=\"([^\"]+)\"")
                .find(it)?.groupValues?.get(1)
        } ?: return null
        val part = parts["word/${target.removePrefix("/word/")}"] ?: return null
        return fieldText(part.toString(StandardCharsets.UTF_8))
    }

    private fun fieldText(xml: String): String {
        var text = xml
            // The token is wrapped in a w:t element so the text extraction below keeps it: a bare
            // "{PAGE}" would sit outside every w:t and be dropped.
            .replace(
                Regex("<w:fldSimple w:instr=\" PAGE \".*?</w:fldSimple>", RegexOption.DOT_MATCHES_ALL),
                "<w:t>{PAGE}</w:t>",
            )
            .replace(
                Regex("<w:fldSimple w:instr=\" NUMPAGES \".*?</w:fldSimple>", RegexOption.DOT_MATCHES_ALL),
                "<w:t>{NUMPAGES}</w:t>",
            )
            .replace("<w:br/>", "\n")
            .replace("<w:tab/>", "\t")
        text = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL).findAll(text)
            .joinToString("") { unescapeXml(it.groupValues[1]) }
        return text
    }

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

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
        val needsRelNs = hrefIds.isNotEmpty() || imageParts.isNotEmpty() ||
            doc.layout?.header != null || doc.layout?.footer != null
        val relNs = if (!needsRelNs) "" else """ xmlns:r="$REL_NS""""
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
                    val widths = b.columnWidthsTwips
                    if (widths == null) {
                        append("<w:tbl><w:tblPr/><w:tblGrid>")
                        repeat(b.rows[0].size) { append("<w:gridCol/>") }
                    } else {
                        append("<w:tbl><w:tblPr><w:tblW w:w=\"${widths.sum()}\" w:type=\"dxa\"/>")
                        append("<w:tblLayout w:type=\"fixed\"/></w:tblPr><w:tblGrid>")
                        for (width in widths) append("<w:gridCol w:w=\"$width\"/>")
                    }
                    append("</w:tblGrid>")
                    for ((rowIndex, row) in b.rows.withIndex()) {
                        append(if (b.repeatHeaderRow && rowIndex == 0) "<w:tr><w:trPr><w:tblHeader/></w:trPr>" else "<w:tr>")
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
        appendSectPr(
            doc.layout,
            headerRelId = if (doc.layout?.header != null) HEADER_REL_ID else null,
            footerRelId = if (doc.layout?.footer != null) FOOTER_REL_ID else null,
            headerFirstRelId = if (doc.layout?.firstPageHeader != null) HEADER_FIRST_REL_ID else null,
            footerFirstRelId = if (doc.layout?.firstPageFooter != null) FOOTER_FIRST_REL_ID else null,
            headerEvenRelId = if (doc.layout?.evenPageHeader != null) HEADER_EVEN_REL_ID else null,
            footerEvenRelId = if (doc.layout?.evenPageFooter != null) FOOTER_EVEN_REL_ID else null,
        )
        append("</w:body></w:document>")
    }

    /**
     * Emits the section properties. A document without a [Layout] keeps the byte-identical
     * `<w:sectPr/>` the pinned legacy writer produced.
     */
    private fun StringBuilder.appendSectPr(
        layout: Layout?,
        headerRelId: String?,
        footerRelId: String?,
        headerFirstRelId: String? = null,
        footerFirstRelId: String? = null,
        headerEvenRelId: String? = null,
        footerEvenRelId: String? = null,
    ) {
        if (layout == null) {
            append("<w:sectPr/>")
            return
        }
        append("<w:sectPr>")
        if (headerRelId != null) append("""<w:headerReference w:type="default" r:id="$headerRelId"/>""")
        if (headerFirstRelId != null) append("""<w:headerReference w:type="first" r:id="$headerFirstRelId"/>""")
        if (headerEvenRelId != null) append("""<w:headerReference w:type="even" r:id="$headerEvenRelId"/>""")
        if (footerRelId != null) append("""<w:footerReference w:type="default" r:id="$footerRelId"/>""")
        if (footerFirstRelId != null) append("""<w:footerReference w:type="first" r:id="$footerFirstRelId"/>""")
        if (footerEvenRelId != null) append("""<w:footerReference w:type="even" r:id="$footerEvenRelId"/>""")
        // CT_SectPr orders its children: references, type, pgSz, pgMar, pgNumType, cols. A lenient
        // reader accepts any order; a schema-validating one does not, so the order is enforced here.
        if (layout.sectionType != Layout.SectionType.NEXT_PAGE) {
            append("""<w:type w:val="${layout.sectionType.value}"/>""")
        }
        val page = layout.pageSetup
        append("""<w:pgSz w:w="${page.widthTwips}" w:h="${page.heightTwips}"""")
        if (page.orientation == PageSetup.Orientation.LANDSCAPE) append(""" w:orient="landscape"""")
        append("/>")
        append(
            """<w:pgMar w:top="${page.marginTopTwips}" w:right="${page.marginRightTwips}" """ +
                """w:bottom="${page.marginBottomTwips}" w:left="${page.marginLeftTwips}" """ +
                """w:header="${page.headerTwips}" w:footer="${page.footerTwips}" w:gutter="0"/>""",
        )
        // CT_SectPr order: lnNumType precedes pgNumType and cols; vAlign follows cols.
        layout.lineNumbering?.let { numbering ->
            append("""<w:lnNumType w:countBy="${numbering.countBy}" w:start="${numbering.start}"""")
            if (numbering.restart != Layout.LineNumbering.Restart.NEW_PAGE) {
                append(""" w:restart="${numbering.restart.value}"""")
            }
            append("/>")
        }
        if (layout.pageNumberStart != null) {
            append("""<w:pgNumType w:start="${layout.pageNumberStart}"/>""")
        }
        if (layout.columns > 1) {
            append("""<w:cols w:num="${layout.columns}" w:space="${layout.columnSpaceTwips}"/>""")
        }
        if (layout.verticalAlignment != Layout.VerticalAlignment.TOP) {
            append("""<w:vAlign w:val="${layout.verticalAlignment.value}"/>""")
        }
        // `w:titlePg` follows `w:cols` in CT_SectPr and exists only when a first-page part does.
        if (layout.firstPageHeader != null || layout.firstPageFooter != null) append("<w:titlePg/>")
        append("</w:sectPr>")
    }

    /**
     * A header (`hdr`) or footer (`ftr`) part. `{PAGE}` and `{NUMPAGES}` become `w:fldSimple`
     * fields with a cached `1`; every other character stays literal text.
     */
    private fun headerFooterXml(kind: String, text: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:$kind xmlns:w="$WORD_NS"><w:p>""")
        var index = 0
        val literal = StringBuilder()
        fun flush() {
            if (literal.isEmpty()) return
            append("<w:r>")
            appendTextContent(literal.toString())
            append("</w:r>")
            literal.setLength(0)
        }
        while (index < text.length) {
            when {
                text.startsWith("{PAGE}", index) -> {
                    flush()
                    append("""<w:fldSimple w:instr=" PAGE "><w:r><w:t>1</w:t></w:r></w:fldSimple>""")
                    index += "{PAGE}".length
                }
                text.startsWith("{NUMPAGES}", index) -> {
                    flush()
                    append("""<w:fldSimple w:instr=" NUMPAGES "><w:r><w:t>1</w:t></w:r></w:fldSimple>""")
                    index += "{NUMPAGES}".length
                }
                else -> { literal.append(text[index]); index++ }
            }
        }
        flush()
        append("</w:p></w:$kind>")
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
        // Import stays lenient: geometry is captured only when the table is fully specified, and a
        // foreign table with partial widths imports as an auto-fit table instead of failing.
        val gridWidths = tblNode.children.firstOrNull { it.name.local == "tblGrid" }
            ?.children?.filter { it.name.local == "gridCol" }
            ?.map { wAttr(it, "w")?.toIntOrNull() }
        val columnWidths = gridWidths
            ?.takeIf { it.size == width && it.all { cell -> cell != null } }
            ?.map { it!! }
        val headerRow = tblNode.children.firstOrNull { it.name.local == "tr" }?.children
            ?.firstOrNull { it.name.local == "trPr" }?.children
            ?.any { it.name.local == "tblHeader" } == true
        return Block.Table(normalized, columnWidths, headerRow)
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

    private fun contentTypesXml(
        imageFormats: List<ImageFormat> = emptyList(),
        hasHeader: Boolean = false,
        hasFooter: Boolean = false,
        hasFirstHeader: Boolean = false,
        hasFirstFooter: Boolean = false,
        hasEvenHeader: Boolean = false,
        hasEvenFooter: Boolean = false,
        hasSettings: Boolean = false,
    ): String =
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
            (if (hasHeader) """<Override PartName="/$HEADER_PART" ContentType="$HEADER_CONTENT_TYPE"/>""" else "") +
            (if (hasFooter) """<Override PartName="/$FOOTER_PART" ContentType="$FOOTER_CONTENT_TYPE"/>""" else "") +
            (if (hasFirstHeader) """<Override PartName="/$HEADER_FIRST_PART" ContentType="$HEADER_CONTENT_TYPE"/>""" else "") +
            (if (hasFirstFooter) """<Override PartName="/$FOOTER_FIRST_PART" ContentType="$FOOTER_CONTENT_TYPE"/>""" else "") +
            (if (hasEvenHeader) """<Override PartName="/$HEADER_EVEN_PART" ContentType="$HEADER_CONTENT_TYPE"/>""" else "") +
            (if (hasEvenFooter) """<Override PartName="/$FOOTER_EVEN_PART" ContentType="$FOOTER_CONTENT_TYPE"/>""" else "") +
            (if (hasSettings) """<Override PartName="/$SETTINGS_PART" ContentType="$SETTINGS_CONTENT_TYPE"/>""" else "") +
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
        headerRelId: String? = null,
        footerRelId: String? = null,
        headerFirstRelId: String? = null,
        footerFirstRelId: String? = null,
        headerEvenRelId: String? = null,
        footerEvenRelId: String? = null,
        settingsRelId: String? = null,
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
            (if (headerRelId != null) {
                """<Relationship Id="$headerRelId" Type="$HEADER_REL_TYPE" Target="header1.xml"/>"""
            } else "") +
            (if (footerRelId != null) {
                """<Relationship Id="$footerRelId" Type="$FOOTER_REL_TYPE" Target="footer1.xml"/>"""
            } else "") +
            (if (headerFirstRelId != null) {
                """<Relationship Id="$headerFirstRelId" Type="$HEADER_REL_TYPE" Target="header2.xml"/>"""
            } else "") +
            (if (footerFirstRelId != null) {
                """<Relationship Id="$footerFirstRelId" Type="$FOOTER_REL_TYPE" Target="footer2.xml"/>"""
            } else "") +
            (if (headerEvenRelId != null) {
                """<Relationship Id="$headerEvenRelId" Type="$HEADER_REL_TYPE" Target="header3.xml"/>"""
            } else "") +
            (if (footerEvenRelId != null) {
                """<Relationship Id="$footerEvenRelId" Type="$FOOTER_REL_TYPE" Target="footer3.xml"/>"""
            } else "") +
            (if (settingsRelId != null) {
                """<Relationship Id="$settingsRelId" Type="$SETTINGS_REL_TYPE" Target="settings.xml"/>"""
            } else "") +
            """</Relationships>"""

    /**
     * `word/settings.xml` with the single element this writer needs. Word ignores an `even`
     * header/footer reference unless `w:evenAndOddHeaders` is set, so the part exists exactly when
     * an even-page header or footer does.
     */
    private fun settingsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:settings xmlns:w="$WORD_NS"><w:evenAndOddHeaders/></w:settings>"""

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
