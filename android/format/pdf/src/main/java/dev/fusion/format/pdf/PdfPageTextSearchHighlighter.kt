// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/**
 * Clean-room PDF Content-Stream (`BT .. ET`) Text-Run Extractor & Search Quad Highlighter (`FF-03`, `PR-08`).
 *
 * Parses uncompressed PDF page content streams (`Tf`, `Td`/`TD`/`Tm`, `Tj`, `TJ`), computes baseline
 * glyph bounding boxes in PDF user-space points (`[x0, y0, x1, y1]`), and locates exact highlight
 * quads + context snippets for case-insensitive queries.
 */
object PdfPageTextSearchHighlighter {
    data class TextRunBox(
        val text: String,
        val xPt: Double,
        val yPt: Double,
        val widthPt: Double,
        val heightPt: Double,
    )

    data class HighlightQuad(
        val matchedText: String,
        val snippet: String,
        val x0Pt: Double,
        val y0Pt: Double,
        val x1Pt: Double,
        val y1Pt: Double,
    )

    fun extractTextRuns(contentStream: String): List<TextRunBox> {
        val runs = ArrayList<TextRunBox>()
        val btBlocks = Regex("""\bBT\b(.*?)\bET\b""", RegexOption.DOT_MATCHES_ALL).findAll(contentStream)
        for (block in btBlocks) {
            var curX = 0.0
            var curY = 0.0
            var fontSize = 12.0
            for (rawLine in block.groupValues[1].lines()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                Regex("""/F\d+\s+([-0-9.]+)\s+Tf""").find(line)?.let {
                    fontSize = it.groupValues[1].toDoubleOrNull()?.coerceAtLeast(1.0) ?: fontSize
                }
                Regex("""([-0-9.]+)\s+([-0-9.]+)\s+Td""").find(line)?.let {
                    curX += it.groupValues[1].toDoubleOrNull() ?: 0.0
                    curY += it.groupValues[2].toDoubleOrNull() ?: 0.0
                }
                Regex("""([-0-9.]+)\s+([-0-9.]+)\s+([-0-9.]+)\s+([-0-9.]+)\s+([-0-9.]+)\s+([-0-9.]+)\s+Tm""")
                    .find(line)?.let {
                        val scaleY = kotlin.math.abs(it.groupValues[4].toDoubleOrNull() ?: 1.0)
                        if (scaleY > 1.0 && fontSize == 12.0) fontSize = scaleY
                        curX = it.groupValues[5].toDoubleOrNull() ?: curX
                        curY = it.groupValues[6].toDoubleOrNull() ?: curY
                    }
                Regex("""\(([^)]*)\)\s*Tj""").find(line)?.let { m ->
                    val txt = unescape(m.groupValues[1])
                    if (txt.isNotEmpty()) {
                        val w = txt.length * fontSize * 0.55
                        runs.add(TextRunBox(txt, curX, curY, w, fontSize))
                        curX += w
                    }
                }
                Regex("""\[(.*?)]\s*TJ""").find(line)?.let { m ->
                    val sb = StringBuilder()
                    for (sub in Regex("""\(([^)]*)\)""").findAll(m.groupValues[1])) {
                        sb.append(unescape(sub.groupValues[1]))
                    }
                    val txt = sb.toString()
                    if (txt.isNotEmpty()) {
                        val w = txt.length * fontSize * 0.55
                        runs.add(TextRunBox(txt, curX, curY, w, fontSize))
                        curX += w
                    }
                }
            }
        }
        return runs
    }

    fun searchHighlights(contentStream: String, query: String): List<HighlightQuad> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val runs = extractTextRuns(contentStream)
        val out = ArrayList<HighlightQuad>()
        for (run in runs) {
            var start = 0
            while (start <= run.text.length - q.length) {
                val hit = run.text.indexOf(q, startIndex = start, ignoreCase = true)
                if (hit < 0) break
                val glyphW = if (run.text.isEmpty()) 0.0 else run.widthPt / run.text.length
                val x0 = run.xPt + hit * glyphW
                val x1 = x0 + q.length * glyphW
                val ctxStart = (hit - 12).coerceAtLeast(0)
                val ctxEnd = (hit + q.length + 12).coerceAtMost(run.text.length)
                out.add(
                    HighlightQuad(
                        matchedText = run.text.substring(hit, hit + q.length),
                        snippet = run.text.substring(ctxStart, ctxEnd),
                        x0Pt = x0,
                        y0Pt = run.yPt,
                        x1Pt = x1,
                        y1Pt = run.yPt + run.heightPt,
                    ),
                )
                start = hit + q.length
            }
        }
        return out
    }

    private fun unescape(s: String): String = s
        .replace("\\(", "(")
        .replace("\\)", ")")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")
}
