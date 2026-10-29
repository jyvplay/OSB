// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Clean-room ISO 32000-1:2008 PDF 1.7 paginated vector document & table writer.
 *
 * Converts structured Writer blocks (headings, paragraphs, lists, code blocks, tables) into
 * multi-page selectable-text PDFs with zero JNI, zero Chromium, and zero LibreOffice dependencies
 * (architecture inspired by Apache-2.0 `PDFBox` & `Typst` / `office2pdf`).
 *
 * Guarantees:
 * - Zero creation/modification timestamp metadata (`mat2`/reproducible privacy invariant: byte-identical
 *   PDF output across runs).
 * - Exact byte-offset `xref` table, `trailer << /Size ... /Root ... >>`, and `startxref`.
 * - Multi-page automatic pagination with running header and `"Page i of N"` footer.
 */
object PdfPageWriter {
    const val SCOPE =
        "Clean-room ISO 32000-1 PDF 1.7 paginated vector writer (Standard-14 Helvetica/Helvetica-Bold/Courier, word wrap, bordered tables, zero-metadata reproducible xref); WinAnsi printable ASCII/Latin-1 subset only (TrueType CID/CJK font embedding out of scope)."

    const val US_LETTER_W = 612.0
    const val US_LETTER_H = 792.0
    const val A4_W = 595.28
    const val A4_H = 841.89
    const val MAX_BLOCKS = 4_096

    data class PageSetup(
        val widthPt: Double = US_LETTER_W,
        val heightPt: Double = US_LETTER_H,
        val marginPt: Double = 54.0,
        val headerTitle: String = "",
        val showPageNumbers: Boolean = true,
    ) {
        init {
            require(widthPt in 200.0..2000.0 && heightPt in 200.0..2000.0) { "invalid page dimensions" }
            require(marginPt >= 24.0 && marginPt * 2 < widthPt - 40 && marginPt * 2 < heightPt - 60) {
                "invalid page margin $marginPt"
            }
        }
    }

    sealed interface Element {
        data class Heading(val level: Int, val text: String) : Element {
            init {
                require(level in 1..4) { "heading level $level outside 1..4" }
            }
        }

        data class Paragraph(val text: String, val monospace: Boolean = false) : Element
        data class BulletItem(val marker: String, val text: String, val indentLevel: Int = 0) : Element {
            init {
                require(indentLevel in 0..6) { "indentLevel $indentLevel outside 0..6" }
            }
        }

        data class Table(val rows: List<List<String>>, val hasHeaderRow: Boolean = true) : Element {
            init {
                require(rows.isNotEmpty() && rows.first().isNotEmpty()) { "table must not be empty" }
                val cols = rows.first().size
                require(rows.all { it.size == cols }) { "ragged table rows" }
            }
        }
    }

    data class RenderedPdf(
        val bytes: ByteArray,
        val pageCount: Int,
        val xrefOffset: Int,
    )

    fun render(elements: List<Element>, setup: PageSetup = PageSetup()): RenderedPdf {
        require(elements.size <= MAX_BLOCKS) { "PDF exceeds $MAX_BLOCKS blocks" }
        val usableW = setup.widthPt - 2.0 * setup.marginPt
        val topY = setup.heightPt - setup.marginPt - (if (setup.headerTitle.isNotEmpty()) 18.0 else 0.0)
        val bottomY = setup.marginPt + (if (setup.showPageNumbers) 20.0 else 0.0)

        val pageOps = ArrayList<StringBuilder>()
        var cur = StringBuilder()
        pageOps.add(cur)
        var y = topY

        fun nextPage() {
            cur = StringBuilder()
            pageOps.add(cur)
            y = topY
        }

        fun ensureSpace(neededPt: Double) {
            if (y - neededPt < bottomY) nextPage()
        }

        for (el in elements) {
            when (el) {
                is Element.Heading -> {
                    val fontSize = when (el.level) {
                        1 -> 18.0
                        2 -> 15.0
                        3 -> 13.0
                        else -> 11.5
                    }
                    val lineH = fontSize * 1.35
                    val lines = wrapText(el.text, usableW, fontSize, monospace = false)
                    ensureSpace(lineH * lines.size + 8.0)
                    y -= 4.0
                    for (ln in lines) {
                        ensureSpace(lineH)
                        y -= fontSize
                        emitText(cur, "F2", fontSize, setup.marginPt, y, ln)
                        y -= (lineH - fontSize)
                    }
                    y -= 4.0
                }
                is Element.Paragraph -> {
                    val fontSize = if (el.monospace) 9.5 else 11.0
                    val fontRef = if (el.monospace) "F3" else "F1"
                    val lineH = fontSize * 1.4
                    val lines = wrapText(el.text, usableW, fontSize, el.monospace)
                    for (ln in lines) {
                        ensureSpace(lineH)
                        y -= fontSize
                        emitText(cur, fontRef, fontSize, setup.marginPt, y, ln)
                        y -= (lineH - fontSize)
                    }
                    y -= 6.0
                }
                is Element.BulletItem -> {
                    val indent = el.indentLevel * 18.0
                    val fontSize = 11.0
                    val lineH = fontSize * 1.35
                    val bodyW = (usableW - indent - 20.0).coerceAtLeast(60.0)
                    val lines = wrapText(el.text, bodyW, fontSize, false)
                    for ((i, ln) in lines.withIndex()) {
                        ensureSpace(lineH)
                        y -= fontSize
                        if (i == 0) emitText(cur, "F2", fontSize, setup.marginPt + indent, y, sanitizeWinAnsi(el.marker))
                        emitText(cur, "F1", fontSize, setup.marginPt + indent + 20.0, y, ln)
                        y -= (lineH - fontSize)
                    }
                    y -= 2.0
                }
                is Element.Table -> {
                    val cols = el.rows.first().size
                    val colW = usableW / cols
                    val rowH = 20.0
                    for ((rIdx, row) in el.rows.withIndex()) {
                        ensureSpace(rowH + 2.0)
                        val rowTop = y
                        val rowBottom = y - rowH
                        if (rIdx == 0 && el.hasHeaderRow) {
                            cur.append("0.91 0.94 0.98 rg ")
                            cur.append("${fmt(setup.marginPt)} ${fmt(rowBottom)} ${fmt(usableW)} ${fmt(rowH)} re f 0 0 0 rg\n")
                        }
                        cur.append("0.65 0.71 0.80 RG 0.6 w ")
                        for ((cIdx, cellText) in row.withIndex()) {
                            val cx = setup.marginPt + cIdx * colW
                            cur.append("${fmt(cx)} ${fmt(rowBottom)} ${fmt(colW)} ${fmt(rowH)} re S\n")
                            val fontRef = if (rIdx == 0 && el.hasHeaderRow) "F2" else "F1"
                            val clipped = fitSingleLine(cellText, (colW - 8.0).coerceAtLeast(16.0), 9.5)
                            emitText(cur, fontRef, 9.5, cx + 4.0, rowBottom + 6.0, clipped)
                        }
                        cur.append("0 0 0 RG\n")
                        y = rowTop - rowH
                    }
                    y -= 8.0
                }
            }
        }

        val totalPages = pageOps.size
        for ((pIdx, sb) in pageOps.withIndex()) {
            if (setup.headerTitle.isNotEmpty()) {
                val hdrY = setup.heightPt - setup.marginPt + 4.0
                sb.append("0.40 0.45 0.55 rg\n")
                emitText(sb, "F1", 9.0, setup.marginPt, hdrY, fitSingleLine(setup.headerTitle, usableW, 9.0))
                sb.append("0 0 0 rg\n")
            }
            if (setup.showPageNumbers) {
                val ftrText = "Page ${pIdx + 1} of $totalPages"
                val ftrY = setup.marginPt - 14.0
                sb.append("0.40 0.45 0.55 rg\n")
                emitText(sb, "F1", 9.0, setup.marginPt, ftrY, ftrText)
                sb.append("0 0 0 rg\n")
            }
        }

        // Object numbering:
        // 1: Catalog, 2: Pages, 3: Font F1 (Helvetica), 4: Font F2 (Helvetica-Bold), 5: Font F3 (Courier)
        // For each page i in 0 until N: pageObj = 6 + 2*i, contentObj = 7 + 2*i
        val objects = ArrayList<ByteArray>()
        val kidsRefs = (0 until totalPages).joinToString(" ") { "${6 + 2 * it} 0 R" }
        objects.add("<< /Type /Catalog /Pages 2 0 R >>".toByteArray(StandardCharsets.US_ASCII))
        objects.add("<< /Type /Pages /Kids [ $kidsRefs ] /Count $totalPages >>".toByteArray(StandardCharsets.US_ASCII))
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>".toByteArray(StandardCharsets.US_ASCII))
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>".toByteArray(StandardCharsets.US_ASCII))
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>".toByteArray(StandardCharsets.US_ASCII))

        for (i in 0 until totalPages) {
            val contentObjId = 7 + 2 * i
            val pageDict = "<< /Type /Page /Parent 2 0 R " +
                "/MediaBox [ 0 0 ${fmt(setup.widthPt)} ${fmt(setup.heightPt)} ] " +
                "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> " +
                "/Contents $contentObjId 0 R >>"
            objects.add(pageDict.toByteArray(StandardCharsets.US_ASCII))

            val streamBytes = pageOps[i].toString().toByteArray(StandardCharsets.ISO_8859_1)
            val streamOut = ByteArrayOutputStream()
            streamOut.write("<< /Length ${streamBytes.size} >>\nstream\n".toByteArray(StandardCharsets.US_ASCII))
            streamOut.write(streamBytes)
            streamOut.write("\nendstream".toByteArray(StandardCharsets.US_ASCII))
            objects.add(streamOut.toByteArray())
        }

        val out = ByteArrayOutputStream()
        // PDF 1.7 header + 4 high-bit binary comment bytes per ISO 32000-1 §7.5.2
        out.write("%PDF-1.7\n%\u00E2\u00E3\u00CF\u00D3\n".toByteArray(StandardCharsets.ISO_8859_1))

        val offsets = IntArray(objects.size + 1)
        for ((idx, body) in objects.withIndex()) {
            val objNum = idx + 1
            offsets[objNum] = out.size()
            out.write("$objNum 0 obj\n".toByteArray(StandardCharsets.US_ASCII))
            out.write(body)
            out.write("\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
        }

        val xrefOffset = out.size()
        val totalObjs = objects.size + 1
        out.write("xref\n0 $totalObjs\n".toByteArray(StandardCharsets.US_ASCII))
        out.write("0000000000 65535 f \n".toByteArray(StandardCharsets.US_ASCII))
        for (objNum in 1..objects.size) {
            val line = String.format(Locale.ROOT, "%010d 00000 n \n", offsets[objNum])
            out.write(line.toByteArray(StandardCharsets.US_ASCII))
        }
        val trailer = "trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
        out.write(trailer.toByteArray(StandardCharsets.US_ASCII))
        return RenderedPdf(out.toByteArray(), totalPages, xrefOffset)
    }

    private fun emitText(sb: StringBuilder, font: String, size: Double, x: Double, y: Double, text: String) {
        sb.append("BT /$font ${fmt(size)} Tf ${fmt(x)} ${fmt(y)} Td (${escapePdfString(sanitizeWinAnsi(text))}) Tj ET\n")
    }

    private fun wrapText(raw: String, maxWidthPt: Double, fontSize: Double, monospace: Boolean): List<String> {
        val avgCharW = fontSize * (if (monospace) 0.60 else 0.52)
        val maxChars = (maxWidthPt / avgCharW).toInt().coerceAtLeast(8)
        val paragraphs = raw.replace("\r\n", "\n").split('\n')
        val out = ArrayList<String>()
        for (para in paragraphs) {
            if (para.isBlank()) {
                out.add("")
                continue
            }
            val words = para.trim().split(Regex("\\s+"))
            var cur = StringBuilder()
            for (w in words) {
                if (w.length > maxChars) {
                    if (cur.isNotEmpty()) {
                        out.add(cur.toString())
                        cur = StringBuilder()
                    }
                    var k = 0
                    while (k < w.length) {
                        val end = (k + maxChars).coerceAtMost(w.length)
                        out.add(w.substring(k, end))
                        k = end
                    }
                } else if (cur.isEmpty()) {
                    cur.append(w)
                } else if (cur.length + 1 + w.length <= maxChars) {
                    cur.append(' ').append(w)
                } else {
                    out.add(cur.toString())
                    cur = StringBuilder(w)
                }
            }
            if (cur.isNotEmpty()) out.add(cur.toString())
        }
        return if (out.isEmpty()) listOf("") else out
    }

    private fun fitSingleLine(raw: String, maxWidthPt: Double, fontSize: Double): String {
        val maxChars = (maxWidthPt / (fontSize * 0.54)).toInt().coerceAtLeast(3)
        val clean = sanitizeWinAnsi(raw.replace('\n', ' ').trim())
        return if (clean.length <= maxChars) clean else clean.substring(0, maxChars - 3) + "..."
    }

    private fun sanitizeWinAnsi(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '\u2014', '\u2013' -> append('-')
                '\u2018', '\u2019' -> append('\'')
                '\u201C', '\u201D' -> append('"')
                '\u2026' -> append("...")
                '\u2022' -> append('*')
                else -> append(if (c.code in 0x20..0xFF) c else '?')
            }
        }
    }

    private fun escapePdfString(s: String): String = buildString(s.length + 8) {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.ROOT, "%.2f", v)
}
