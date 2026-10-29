// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Clean-room GitHub-Flavored Markdown (`GFM` §4.10) Pipe-Table Parser, Column-Aligned
 * Pretty-Printer, Escaped-Pipe (`\|`) Tokenizer, and RFC 4180 CSV Bridge.
 */
object MarkdownTableFmt {
    const val SCOPE =
        "GFM pipe-table parser, column-aligned formatter (:---/:---:/---:), escaped-pipe (\\|) handler, and RFC 4180 CSV converter; multi-line HTML rowspan/colspan cells out of scope."

    const val MAX_ROWS = 10_000
    const val MAX_COLS = 128

    enum class Align { LEFT, CENTER, RIGHT }

    data class Table(
        val headers: List<String>,
        val alignments: List<Align>,
        val rows: List<List<String>>,
    ) {
        val columnCount: Int get() = headers.size

        init {
            require(headers.size in 1..MAX_COLS) { "column count ${headers.size} outside 1..$MAX_COLS" }
            require(alignments.size == headers.size) { "alignments count != headers count" }
            require(rows.size <= MAX_ROWS) { "row count ${rows.size} exceeds $MAX_ROWS" }
            require(rows.all { it.size == headers.size }) { "all table rows must match header column count" }
        }
    }

    fun parseGfm(markdown: String): Table {
        val lines = markdown.replace("\r\n", "\n").trim().split('\n').filter { it.isNotBlank() }
        require(lines.size >= 2) { "GFM table requires at least header + delimiter rows" }
        val headers = splitUnescapedPipes(lines[0])
        val delims = splitUnescapedPipes(lines[1])
        require(headers.isNotEmpty() && headers.size == delims.size) {
            "header column count (${headers.size}) != delimiter count (${delims.size})"
        }
        val aligns = delims.map(::parseAlignmentSpec)
        val body = lines.drop(2).map { ln ->
            val cells = splitUnescapedPipes(ln)
            List(headers.size) { idx -> cells.getOrElse(idx) { "" } }
        }
        return Table(headers, aligns, body)
    }

    fun formatAligned(table: Table): String {
        val escapedHeaders = table.headers.map(::escapeCell)
        val escapedRows = table.rows.map { r -> r.map(::escapeCell) }
        val widths = IntArray(table.columnCount) { c ->
            var w = maxOf(3, escapedHeaders[c].length)
            for (r in escapedRows) if (r[c].length > w) w = r[c].length
            if (table.alignments[c] == Align.CENTER && w < 4) w = 4
            w
        }

        fun padCell(text: String, width: Int, align: Align): String {
            val pad = width - text.length
            return when (align) {
                Align.LEFT -> text + " ".repeat(pad)
                Align.RIGHT -> " ".repeat(pad) + text
                Align.CENTER -> {
                    val left = pad / 2
                    val right = pad - left
                    " ".repeat(left) + text + " ".repeat(right)
                }
            }
        }

        return buildString {
            append("| ")
            append((0 until table.columnCount).joinToString(" | ") { c ->
                padCell(escapedHeaders[c], widths[c], table.alignments[c])
            })
            append(" |\n| ")
            append((0 until table.columnCount).joinToString(" | ") { c ->
                val w = widths[c]
                when (table.alignments[c]) {
                    Align.LEFT -> ":" + "-".repeat(w - 1)
                    Align.RIGHT -> "-".repeat(w - 1) + ":"
                    Align.CENTER -> ":" + "-".repeat(w - 2) + ":"
                }
            })
            append(" |")
            for (r in escapedRows) {
                append("\n| ")
                append((0 until table.columnCount).joinToString(" | ") { c ->
                    padCell(r[c], widths[c], table.alignments[c])
                })
                append(" |")
            }
        }
    }

    fun toCsv(table: Table): String {
        val allRows = ArrayList<List<String>>(1 + table.rows.size)
        allRows.add(table.headers)
        allRows.addAll(table.rows)
        return allRows.joinToString("\n") { row ->
            row.joinToString(",") { cell ->
                if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                    "\"" + cell.replace("\"", "\"\"") + "\""
                } else {
                    cell
                }
            }
        }
    }

    fun fromCsv(csv: String, defaultAlign: Align = Align.LEFT): Table {
        val parsed = parseCsvRows(csv)
        require(parsed.isNotEmpty() && parsed.first().isNotEmpty()) { "CSV must contain a non-empty header row" }
        val hdr = parsed.first()
        val cols = hdr.size
        val rows = parsed.drop(1).map { r -> List(cols) { idx -> r.getOrElse(idx) { "" } } }
        return Table(hdr, List(cols) { defaultAlign }, rows)
    }

    private fun splitUnescapedPipes(line: String): List<String> {
        var trimmed = line.trim()
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) trimmed = trimmed.substring(0, trimmed.length - 1)
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < trimmed.length) {
            if (trimmed[i] == '\\' && i + 1 < trimmed.length && (trimmed[i + 1] == '|' || trimmed[i + 1] == '\\')) {
                cur.append(trimmed[i + 1])
                i += 2
            } else if (trimmed[i] == '|') {
                out.add(cur.toString().trim())
                cur.setLength(0)
                i++
            } else {
                cur.append(trimmed[i])
                i++
            }
        }
        out.add(cur.toString().trim())
        return out
    }

    private fun parseAlignmentSpec(spec: String): Align {
        val s = spec.trim()
        require(Regex("""^:?-+:?$""").matches(s) && s.count { it == '-' } >= 1) {
            "invalid GFM table alignment delimiter '$spec'"
        }
        val l = s.startsWith(":")
        val r = s.endsWith(":")
        return when {
            l && r -> Align.CENTER
            r -> Align.RIGHT
            else -> Align.LEFT
        }
    }

    private fun escapeCell(s: String): String =
        s.replace("\\", "\\\\").replace("|", "\\|").replace("\r\n", " ").replace('\n', ' ')

    private fun parseCsvRows(csv: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var curRow = ArrayList<String>()
        val curCell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csv.length && csv[i + 1] == '"') {
                        curCell.append('"'); i += 2; continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    curCell.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { curRow.add(curCell.toString()); curCell.setLength(0) }
                    '\r' -> {
                        if (i + 1 < csv.length && csv[i + 1] == '\n') i++
                        curRow.add(curCell.toString()); curCell.setLength(0)
                        rows.add(curRow); curRow = ArrayList()
                    }
                    '\n' -> {
                        curRow.add(curCell.toString()); curCell.setLength(0)
                        rows.add(curRow); curRow = ArrayList()
                    }
                    else -> curCell.append(c)
                }
            }
            i++
        }
        require(!inQuotes) { "unclosed quote in CSV input" }
        if (curCell.isNotEmpty() || curRow.isNotEmpty()) {
            curRow.add(curCell.toString())
            rows.add(curRow)
        }
        return rows
    }
}
