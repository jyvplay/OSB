// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room ECMA-376 `PresentationML` (`.pptx`) slide deck writer and structural reader.
 *
 * Produces standards-compliant `16:9` widescreen `.pptx` archives (`9144000 x 5143500` EMUs)
 * containing `[Content_Types].xml`, `_rels/.rels`, `ppt/presentation.xml`,
 * `ppt/_rels/presentation.xml.rels`, `ppt/slides/slideN.xml`, and optional speaker notes
 * (`ppt/notesSlides/notesSlideN.xml`) with:
 * - Zero `docProps/core.xml` / author / host-OS / timestamp metadata leak (`Tails mat2` parity);
 * - Deterministic `1980-01-01T00:00:00Z` ZIP entry timestamps;
 * - Strict refusal of `TargetMode="External"`, VBA `.bin` macros, and OLE active objects on read.
 */
object PptxSlideDeck {
    const val SCOPE =
        "Clean-room ECMA-376 PresentationML (.pptx) slide deck writer & structural reader (16:9 slides, titles, bullets, data tables, speaker notes, deterministic metadata-free ZIP entries, external relationship & macro refusal); master themes, transitions, and embedded video out of scope."

    const val SLIDE_WIDTH_EMU = 9_144_000L
    const val SLIDE_HEIGHT_EMU = 5_143_500L
    const val MAX_SLIDES = 256
    const val MAX_ARCHIVE_BYTES = 16 * 1024 * 1024

    data class SlideSpec(
        val title: String,
        val bullets: List<String> = emptyList(),
        val tableRows: List<List<String>> = emptyList(),
        val speakerNotes: String = "",
    )

    data class DeckSpec(
        val slides: List<SlideSpec>,
    ) {
        init {
            require(slides.size in 1..MAX_SLIDES) { "slides count ${slides.size} outside 1..$MAX_SLIDES" }
        }
    }

    fun writeDeck(deck: DeckSpec): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            putEntry(zos, "[Content_Types].xml", buildContentTypes(deck.slides))
            putEntry(zos, "_rels/.rels", ROOT_RELS)
            putEntry(zos, "ppt/presentation.xml", buildPresentationXml(deck.slides.size))
            putEntry(zos, "ppt/_rels/presentation.xml.rels", buildPresentationRels(deck.slides.size))
            deck.slides.forEachIndexed { idx, slide ->
                val n = idx + 1
                putEntry(zos, "ppt/slides/slide$n.xml", buildSlideXml(slide))
                if (slide.speakerNotes.isNotBlank()) {
                    putEntry(zos, "ppt/slides/_rels/slide$n.xml.rels", buildSlideNotesRel(n))
                    putEntry(zos, "ppt/notesSlides/notesSlide$n.xml", buildNotesXml(slide.speakerNotes))
                }
            }
        }
        return baos.toByteArray()
    }

    fun readDeck(bytes: ByteArray): DeckSpec {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ARCHIVE_BYTES) {
            "pptx archive size ${bytes.size} outside 1..$MAX_ARCHIVE_BYTES"
        }
        val entries = LinkedHashMap<String, String>()
        var totalExpanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val name = e.name
                require(!name.contains("..") && !name.startsWith("/")) { "unsafe ZIP entry path: $name" }
                require(!name.endsWith(".bin", ignoreCase = true)) { "VBA/OLE binary part refused in .pptx: $name" }
                val buf = zis.readBytes()
                totalExpanded += buf.size
                require(totalExpanded <= MAX_ARCHIVE_BYTES * 2L) { "pptx zip expansion limit exceeded" }
                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    val xml = buf.toString(Charsets.UTF_8)
                    require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
                        "XXE DOCTYPE/ENTITY refused in $name"
                    }
                    if (name.endsWith(".rels")) {
                        require(!Regex("TargetMode\\s*=\\s*[\"']External[\"']", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
                            "external tracking relationship refused in $name"
                        }
                    }
                    entries[name] = xml
                }
            }
        }
        require("ppt/presentation.xml" in entries) { "missing ppt/presentation.xml" }
        val slideIndices = entries.keys.mapNotNull { path ->
            Regex("^ppt/slides/slide(\\d+)\\.xml$").matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()
        }.sorted()
        require(slideIndices.isNotEmpty()) { "pptx contains zero slides" }
        val slides = slideIndices.map { n ->
            val xml = entries.getValue("ppt/slides/slide$n.xml")
            val notesXml = entries["ppt/notesSlides/notesSlide$n.xml"].orEmpty()
            parseSlideXml(xml, notesXml)
        }
        return DeckSpec(slides)
    }

    private fun putEntry(zos: ZipOutputStream, name: String, xml: String) {
        val e = ZipEntry(name).apply { time = 315_532_800_000L } // deterministic 1980-01-01 UTC
        zos.putNextEntry(e)
        zos.write(xml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

    private fun buildContentTypes(slides: List<SlideSpec>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
        slides.forEachIndexed { idx, s ->
            val n = idx + 1
            append("""<Override PartName="/ppt/slides/slide$n.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
            if (s.speakerNotes.isNotBlank()) {
                append("""<Override PartName="/ppt/notesSlides/notesSlide$n.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"/>""")
            }
        }
        append("""</Types>""")
    }

    private fun buildPresentationXml(slideCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
        append("""<p:sldIdLst>""")
        for (i in 1..slideCount) {
            append("""<p:sldId id="${255 + i}" r:id="rId$i"/>""")
        }
        append("""</p:sldIdLst>""")
        append("""<p:sldSz cx="$SLIDE_WIDTH_EMU" cy="$SLIDE_HEIGHT_EMU" type="screen16x9"/>""")
        append("""<p:notesSz cx="6858000" cy="9144000"/>""")
        append("""</p:presentation>""")
    }

    private fun buildPresentationRels(slideCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..slideCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
        }
        append("""</Relationships>""")
    }

    private fun buildSlideNotesRel(n: Int): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide$n.xml"/></Relationships>"""

    private fun buildSlideXml(slide: SlideSpec): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
        append("""<p:cSld><p:spTree>""")
        append("""<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>""")
        // Title shape (id=2)
        append("""<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>""")
        append("""<p:spPr><a:xfrm><a:off x="457200" y="274638"/><a:ext cx="8229600" cy="914400"/></a:xfrm></p:spPr>""")
        append("""<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" sz="3200" b="1"/><a:t>${escXml(slide.title)}</a:t></a:r></a:p></p:txBody></p:sp>""")
        // Bullets shape (id=3)
        if (slide.bullets.isNotEmpty()) {
            append("""<p:sp><p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr>""")
            append("""<p:spPr><a:xfrm><a:off x="457200" y="1371600"/><a:ext cx="8229600" cy="2400000"/></a:xfrm></p:spPr>""")
            append("""<p:txBody><a:bodyPr/><a:lstStyle/>""")
            for (b in slide.bullets) {
                append("""<a:p><a:pPr lvl="0"><a:buChar char="•"/></a:pPr><a:r><a:rPr lang="en-US" sz="2000"/><a:t>${escXml(b)}</a:t></a:r></a:p>""")
            }
            append("""</p:txBody></p:sp>""")
        }
        // Optional DrawingML data table graphicFrame (id=4)
        if (slide.tableRows.isNotEmpty()) {
            val cols = slide.tableRows.maxOf { it.size }.coerceAtLeast(1)
            val colW = 8_229_600L / cols
            append("""<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="4" name="DataTable"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>""")
            append("""<p:xfrm><a:off x="457200" y="3600000"/><a:ext cx="8229600" cy="1200000"/></p:xfrm>""")
            append("""<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table"><a:tbl><a:tblPr firstRow="1"/>""")
            append("""<a:tblGrid>""")
            repeat(cols) { append("""<a:gridCol w="$colW"/>""") }
            append("""</a:tblGrid>""")
            for (row in slide.tableRows) {
                append("""<a:tr h="370840">""")
                for (c in 0 until cols) {
                    val cellText = row.getOrElse(c) { "" }
                    append("""<a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>${escXml(cellText)}</a:t></a:r></a:p></a:txBody><a:tcPr/></a:tc>""")
                }
                append("""</a:tr>""")
            }
            append("""</a:tbl></a:graphicData></a:graphic></p:graphicFrame>""")
        }
        append("""</p:spTree></p:cSld></p:sld>""")
    }

    private fun buildNotesXml(notes: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:sp><p:nvSpPr><p:cNvPr id="2" name="Notes"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>${escXml(notes)}</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:notes>"""

    private fun parseSlideXml(xml: String, notesXml: String): SlideSpec {
        val shapes = Regex("<p:sp>([\\s\\S]*?)</p:sp>").findAll(xml).map { it.groupValues[1] }.toList()
        var title = ""
        val bullets = ArrayList<String>()
        for (sp in shapes) {
            val texts = Regex("<a:t>([\\s\\S]*?)</a:t>").findAll(sp).map { unescXml(it.groupValues[1]) }.toList()
            if (sp.contains("""type="title"""")) {
                title = texts.joinToString("")
            } else {
                bullets.addAll(texts)
            }
        }
        val tableRows = ArrayList<List<String>>()
        for (tr in Regex("<a:tr\\b[^>]*>([\\s\\S]*?)</a:tr>").findAll(xml)) {
            val row = Regex("<a:tc>([\\s\\S]*?)</a:tc>").findAll(tr.groupValues[1]).map { tc ->
                Regex("<a:t>([\\s\\S]*?)</a:t>").findAll(tc.groupValues[1]).joinToString("") { unescXml(it.groupValues[1]) }
            }.toList()
            tableRows.add(row)
        }
        val notes = if (notesXml.isBlank()) "" else {
            Regex("<a:t>([\\s\\S]*?)</a:t>").findAll(notesXml).joinToString("") { unescXml(it.groupValues[1]) }
        }
        return SlideSpec(title = title, bullets = bullets, tableRows = tableRows, speakerNotes = notes)
    }

    private fun escXml(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private fun unescXml(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")

    data class SlideMediaTimingSpec(
        val slideNumber: Int,
        val mediaLabel: String,
        val durationMs: Long,
        val autoAdvanceMs: Long,
    )

    data class DeckTimingInspection(
        val slideCount: Int,
        val timedSlides: List<SlideMediaTimingSpec>,
        val totalAutoAdvanceMs: Long,
    )

    fun writeDeckWithMediaTimings(deck: DeckSpec, timings: List<SlideMediaTimingSpec>): ByteArray {
        val bySlide = timings.associateBy { it.slideNumber }
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            putEntry(zos, "[Content_Types].xml", buildContentTypes(deck.slides))
            putEntry(zos, "_rels/.rels", ROOT_RELS)
            putEntry(zos, "ppt/presentation.xml", buildPresentationXml(deck.slides.size))
            putEntry(zos, "ppt/_rels/presentation.xml.rels", buildPresentationRels(deck.slides.size))
            deck.slides.forEachIndexed { idx, slide ->
                val n = idx + 1
                val baseXml = buildSlideXml(slide)
                val tSpec = bySlide[n]
                val finalXml = if (tSpec == null) baseXml else {
                    require(tSpec.durationMs > 0L && tSpec.autoAdvanceMs >= tSpec.durationMs) {
                        "invalid slide timing on slide $n"
                    }
                    val timingBlock = """<p:transition advTm="${tSpec.autoAdvanceMs}"/><p:timing><p:tnLst><p:par><p:cTn id="1" dur="${tSpec.durationMs}" nodeType="tmRoot" mediaLabel="${escXml(tSpec.mediaLabel)}"/></p:par></p:tnLst></p:timing></p:sld>"""
                    baseXml.replace("</p:sld>", timingBlock)
                }
                putEntry(zos, "ppt/slides/slide$n.xml", finalXml)
                if (slide.speakerNotes.isNotBlank()) {
                    putEntry(zos, "ppt/slides/_rels/slide$n.xml.rels", buildSlideNotesRel(n))
                    putEntry(zos, "ppt/notesSlides/notesSlide$n.xml", buildNotesXml(slide.speakerNotes))
                }
            }
        }
        return baos.toByteArray()
    }

    fun inspectSlideMediaTimings(pptxBytes: ByteArray): DeckTimingInspection {
        val parsed = readDeck(pptxBytes)
        val timed = ArrayList<SlideMediaTimingSpec>()
        ZipInputStream(ByteArrayInputStream(pptxBytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val m = Regex("^ppt/slides/slide(\\d+)\\.xml$").matchEntire(e.name)
                if (m != null) {
                    val slideNo = m.groupValues[1].toInt()
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    val adv = Regex("""<p:transition[^>]*advTm="(\d+)"""").find(xml)?.groupValues?.get(1)?.toLongOrNull()
                    val ctn = Regex("""<p:cTn[^>]*dur="(\d+)"[^>]*mediaLabel="([^"]*)"""").find(xml)
                    if (adv != null && ctn != null) {
                        timed.add(SlideMediaTimingSpec(slideNo, unescXml(ctn.groupValues[2]), ctn.groupValues[1].toLong(), adv))
                    }
                }
            }
        }
        timed.sortBy { it.slideNumber }
        return DeckTimingInspection(parsed.slides.size, timed, timed.sumOf { it.autoAdvanceMs })
    }
}
