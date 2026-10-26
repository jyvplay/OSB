// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/** Detected field separator. SINGLE means no separator won (one column). */
enum class CsvSeparator(val char: Char) { COMMA(','), SEMICOLON(';'), TAB('\t'), PIPE('|'), SINGLE(',') }

/**
 * Delimiter sniffing plus quote-aware translation into the comma form that
 * [CsvTable.parse] accepts. Sniffing scores candidate separators over the
 * first sample lines: a candidate earns its total outside-quote occurrences,
 * doubled when every sampled line carries the same count, so consistent
 * tables beat stray punctuation. Ties break toward COMMA, the interchange
 * default. Translation rewrites the winning separator to commas, preserving
 * quoted fields (including embedded separators, quotes, and newlines) and
 * refusing malformed quoting instead of guessing.
 */
object CsvDialect {
    const val MAX_TEXT = 2 * 1024 * 1024
    const val SAMPLE_LINES = 32
    private val CANDIDATES = listOf(CsvSeparator.COMMA, CsvSeparator.SEMICOLON, CsvSeparator.TAB, CsvSeparator.PIPE)

    data class Sniff(val separator: CsvSeparator, val columns: Int)

    fun sniff(text: String): Sniff {
        require(text.length <= MAX_TEXT)
        val lines = sampleLines(text, SAMPLE_LINES)
        var best = CsvSeparator.SINGLE to 1
        var bestScore = -1
        for (c in CANDIDATES) {
            val counts = lines.map { countOutsideQuotes(it, c.char) }
            val total = counts.sum()
            if (total == 0) continue
            val consistent = counts.toSet().size == 1
            val score = total * (if (consistent) 2 else 1)
            if (score > bestScore) {
                bestScore = score
                best = c to (counts.maxOrNull()!! + 1)
            }
        }
        return Sniff(best.first, best.second)
    }

    fun translate(text: String, separator: CsvSeparator): String {
        require(text.length <= MAX_TEXT)
        if (separator == CsvSeparator.COMMA || separator == CsvSeparator.SINGLE) return text
        val d = separator.char
        val out = StringBuilder(text.length)
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                out.append(c)
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { out.append('"'); i++ }
                    else quoted = false
                }
            } else {
                if (c == '"') { quoted = true; out.append(c) }
                else if (c == d) out.append(',')
                else out.append(c)
            }
            i++
        }
        require(!quoted) { "unterminated quote" }
        return out.toString()
    }

    fun sampleLines(text: String, max: Int): List<String> {
        val lines = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length && lines.size < max) {
            val c = text[i]
            if (quoted) {
                cur.append(c)
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cur.append('"'); i++ }
                    else quoted = false
                }
            } else {
                when (c) {
                    '"' -> { quoted = true; cur.append(c) }
                    '\n' -> { lines.add(cur.toString()); cur.setLength(0) }
                    '\r' -> {
                        lines.add(cur.toString()); cur.setLength(0)
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                    }
                    else -> cur.append(c)
                }
            }
            i++
        }
        if (cur.isNotEmpty() && lines.size < max) lines.add(cur.toString())
        return lines.filter { it.isNotEmpty() }
    }

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var n = 0; var quoted = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') i++ else quoted = false
                }
            } else {
                if (c == '"') quoted = true else if (c == d) n++
            }
            i++
        }
        return n
    }
}
