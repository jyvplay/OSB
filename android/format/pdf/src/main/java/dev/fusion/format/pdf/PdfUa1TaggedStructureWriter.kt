package dev.fusion.format.pdf

import java.nio.charset.StandardCharsets

/**
 * Clean-room ISO 14289-1 (`PDF/UA-1`, Universal Accessibility) Tagged PDF Structure Writer &
 * Conformance Auditor (inspired by Typst 0.14–0.15 accessible PDF tagging; Apache-2.0).
 *
 * Emits a valid PDF 1.7 file containing:
 * - `/MarkInfo << /Marked true >>`, `/Lang (en-US)`, `/ViewerPreferences << /DisplayDocTitle true >>`,
 * - `/Info << /Title (...) >>` + `pdfuaid:part` XMP metadata packet,
 * - `/StructTreeRoot` with `Document` -> `H1`..`H6`, `P`, `Table`, `Figure` (`/Alt (...)`)
 *   structure elements and `/ParentTree` mapping `/StructParents 0` -> `/MCID` array,
 * - Marked-content operators `/H1 <</MCID 0>> BDC ... EMC` in page content streams,
 * and enforces strict heading-hierarchy (no `H1 -> H3` skipped levels) & mandatory figure `/Alt`!
 */
object PdfUa1TaggedStructureWriter {
    const val SCOPE =
        "ISO 14289-1 PDF/UA-1 Tagged PDF generator & auditor (/MarkInfo /Marked true, /Lang, /StructTreeRoot, /ParentTree, BDC/EMC /MCID, /Alt & heading-skip enforcement)"

    sealed interface TaggedElement {
        data class Heading(val level: Int, val text: String) : TaggedElement {
            init {
                require(level in 1..6 && text.isNotBlank()) { "Heading requires level 1..6 and non-blank text" }
            }
        }
        data class Paragraph(val text: String) : TaggedElement {
            init {
                require(text.isNotBlank()) { "Paragraph must not be blank" }
            }
        }
        data class Figure(val caption: String, val altText: String) : TaggedElement
        data class DataTable(val summary: String, val headers: List<String>, val rows: List<List<String>>) : TaggedElement
    }

    data class PdfUaAuditReport(
        val compliant: Boolean,
        val title: String,
        val langTag: String,
        val markedFlag: Boolean,
        val displayDocTitle: Boolean,
        val hasPdfUaIdXmp: Boolean,
        val mcidCount: Int,
        val violations: List<String>,
    )

    fun validateHierarchy(elements: List<TaggedElement>): List<String> {
        val issues = mutableListOf<String>()
        if (elements.isEmpty()) issues.add("EMPTY_DOCUMENT")
        var prevHeadingLevel = 0
        for ((idx, el) in elements.withIndex()) {
            when (el) {
                is TaggedElement.Heading -> {
                    if (prevHeadingLevel == 0 && el.level != 1) {
                        issues.add("FIRST_HEADING_MUST_BE_H1_FOUND_H${el.level}@$idx")
                    } else if (prevHeadingLevel > 0 && el.level > prevHeadingLevel + 1) {
                        issues.add("SKIPPED_HEADING_LEVEL_H${prevHeadingLevel}_TO_H${el.level}@$idx")
                    }
                    prevHeadingLevel = el.level
                }
                is TaggedElement.Figure -> {
                    if (el.altText.isBlank()) issues.add("FIGURE_MISSING_ALT_TEXT@$idx")
                }
                is TaggedElement.DataTable -> {
                    if (el.headers.isEmpty()) issues.add("TABLE_MISSING_TH_HEADERS@$idx")
                }
                is TaggedElement.Paragraph -> {}
            }
        }
        return issues
    }

    fun writeTaggedPdfUa1(
        title: String,
        langTag: String = "en-US",
        elements: List<TaggedElement>,
    ): ByteArray {
        require(title.isNotBlank() && langTag.isNotBlank()) { "PDF/UA-1 requires non-blank title and BCP-47 /Lang" }
        val violations = validateHierarchy(elements)
        require(violations.isEmpty()) { "Refusing non-compliant PDF/UA-1 structure: $violations" }

        // Build content stream with BDC/EMC MCIDs and matching StructElem objects
        val streamSb = StringBuilder("BT /F1 12 Tf 72 740 Td\n")
        val structElems = mutableListOf<Pair<String, String>>() // (tag, extraDictEntries)
        var mcid = 0
        for (el in elements) {
            when (el) {
                is TaggedElement.Heading -> {
                    val tag = "H${el.level}"
                    streamSb.append("/$tag <</MCID $mcid>> BDC (${escPdf(el.text)}) Tj 0 -20 Td EMC\n")
                    structElems.add(tag to "/K $mcid")
                    mcid++
                }
                is TaggedElement.Paragraph -> {
                    streamSb.append("/P <</MCID $mcid>> BDC (${escPdf(el.text)}) Tj 0 -15 Td EMC\n")
                    structElems.add("P" to "/K $mcid")
                    mcid++
                }
                is TaggedElement.Figure -> {
                    streamSb.append("/Figure <</MCID $mcid>> BDC (${escPdf(el.caption)}) Tj 0 -16 Td EMC\n")
                    structElems.add("Figure" to "/Alt (${escPdf(el.altText)}) /K $mcid")
                    mcid++
                }
                is TaggedElement.DataTable -> {
                    val headerLine = el.headers.joinToString(" | ")
                    streamSb.append("/Table <</MCID $mcid>> BDC (${escPdf(headerLine)}) Tj 0 -15 Td EMC\n")
                    structElems.add("Table" to "/Summary (${escPdf(el.summary)}) /K $mcid")
                    mcid++
                }
            }
        }
        streamSb.append("ET\n")
        val contentBytes = streamSb.toString().toByteArray(StandardCharsets.US_ASCII)

        val xmpXml = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?><x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description rdf:about="" xmlns:pdfuaid="http://www.aiim.org/pdfua/ns/id/" xmlns:dc="http://purl.org/dc/elements/1.1/"><pdfuaid:part>1</pdfuaid:part><dc:title><rdf:Alt><rdf:li xml:lang="x-default">${escXml(title)}</rdf:li></rdf:Alt></dc:title></rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end="r"?>"""
        val xmpBytes = xmpXml.toByteArray(StandardCharsets.UTF_8)

        // Object layout:
        // 1 Catalog, 2 Pages, 3 Page, 4 ContentStream, 5 Font (WinAnsiEncoding), 6 Info, 7 Metadata XMP,
        // 8 StructTreeRoot, 9 Document StructElem, 10 ParentTree, 11..(10+N) leaf StructElems
        val firstLeafObj = 11
        val leafRefs = (0 until structElems.size).joinToString(" ") { "${firstLeafObj + it} 0 R" }

        val objs = LinkedHashMap<Int, ByteArray>()
        objs[1] = "<< /Type /Catalog /Pages 2 0 R /Lang (${escPdf(langTag)}) /MarkInfo << /Marked true >> /ViewerPreferences << /DisplayDocTitle true >> /Metadata 7 0 R /StructTreeRoot 8 0 R >>".toByteArray()
        objs[2] = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".toByteArray()
        objs[3] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Tabs /S /StructParents 0 /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>".toByteArray()
        objs[4] = ("<< /Length ${contentBytes.size} >>\nstream\n".toByteArray() + contentBytes + "endstream".toByteArray())
        objs[5] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>".toByteArray()
        objs[6] = "<< /Title (${escPdf(title)}) /Producer (Fusion clean-room PDF/UA-1) >>".toByteArray()
        objs[7] = ("<< /Type /Metadata /Subtype /XML /Length ${xmpBytes.size} >>\nstream\n".toByteArray() + xmpBytes + "\nendstream".toByteArray())
        objs[8] = "<< /Type /StructTreeRoot /K 9 0 R /ParentTree 10 0 R /ParentTreeNextKey 1 >>".toByteArray()
        objs[9] = "<< /Type /StructElem /S /Document /P 8 0 R /K [$leafRefs] >>".toByteArray()
        objs[10] = "<< /Nums [0 [$leafRefs]] >>".toByteArray()
        for ((idx, pair) in structElems.withIndex()) {
            objs[firstLeafObj + idx] = "<< /Type /StructElem /S /${pair.first} /P 9 0 R /Pg 3 0 R ${pair.second} >>".toByteArray()
        }

        val out = StringBuilder("%PDF-1.7\n")
        val offsets = IntArray(objs.size + 1)
        val rawOut = java.io.ByteArrayOutputStream()
        rawOut.write(out.toString().toByteArray(StandardCharsets.US_ASCII))
        for (id in 1..objs.size) {
            offsets[id] = rawOut.size()
            rawOut.write("$id 0 obj\n".toByteArray(StandardCharsets.US_ASCII))
            rawOut.write(objs.getValue(id))
            rawOut.write("\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
        }
        val xrefPos = rawOut.size()
        val trailer = buildString {
            append("xref\n0 ${objs.size + 1}\n")
            append("0000000000 65535 f \n")
            for (id in 1..objs.size) append("%010d 00000 n \n".format(offsets[id]))
            append("trailer\n<< /Size ${objs.size + 1} /Root 1 0 R /Info 6 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        }
        rawOut.write(trailer.toByteArray(StandardCharsets.US_ASCII))
        return rawOut.toByteArray()
    }

    fun auditPdfUa1Bytes(pdfBytes: ByteArray): PdfUaAuditReport {
        val text = String(pdfBytes, StandardCharsets.ISO_8859_1)
        val violations = mutableListOf<String>()
        val marked = Regex("/MarkInfo\\s*<<[^>]*?/Marked\\s+true").containsMatchIn(text)
        val displayTitle = Regex("/ViewerPreferences\\s*<<[^>]*?/DisplayDocTitle\\s+true").containsMatchIn(text)
        val lang = Regex("/Lang\\s*\\(([^)]+)\\)").find(text)?.groupValues?.get(1) ?: ""
        val title = Regex("/Title\\s*\\(([^)]+)\\)").find(text)?.groupValues?.get(1) ?: ""
        val hasXmp = text.contains("<pdfuaid:part>1</pdfuaid:part>")
        val hasStructRoot = text.contains("/Type /StructTreeRoot")
        val hasParentTree = text.contains("/ParentTree ")
        val mcidCount = Regex("/MCID\\s+[0-9]+").findAll(text).count()

        if (!marked) violations.add("MISSING_MARKINFO_MARKED_TRUE")
        if (!displayTitle) violations.add("MISSING_DISPLAY_DOC_TITLE")
        if (lang.isBlank()) violations.add("MISSING_CATALOG_LANG")
        if (title.isBlank()) violations.add("MISSING_DOCUMENT_TITLE")
        if (!hasXmp) violations.add("MISSING_PDFUAID_PART_1_XMP")
        if (!hasStructRoot || !hasParentTree) violations.add("MISSING_STRUCT_TREE_OR_PARENT_TREE")
        if (mcidCount == 0) violations.add("ZERO_MARKED_CONTENT_MCIDS")
        if (Regex("/S\\s*/Figure(?![^>]*?/Alt\\s*\\([^)]+\\))").containsMatchIn(text)) {
            violations.add("FIGURE_WITHOUT_ALT")
        }

        return PdfUaAuditReport(
            compliant = violations.isEmpty(),
            title = title,
            langTag = lang,
            markedFlag = marked,
            displayDocTitle = displayTitle,
            hasPdfUaIdXmp = hasXmp,
            mcidCount = mcidCount,
            violations = violations,
        )
    }

    private fun escPdf(s: String): String = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    private fun escXml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
