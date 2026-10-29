package dev.fusion.notes

/**
 * Clean-room GitHub-Flavored Markdown (`GFM`) Pipe-Table Parser, Column-Aligned Pretty-Printer,
 * and RFC 4180 CSV <-> Markdown Table Converter (Apache-2.0).
 */
object MarkdownTableFormatterEngine {
    const val SCOPE =
        "GFM pipe-table parser + alignment-aware (:---, :---:, ---:) column pretty-printer + RFC 4180 CSV <-> Markdown converter"

    enum class ColAlign { LEFT, CENTER, RIGHT }

    data class GfmTable(
        val headers: List<String>,
        val alignments: List<ColAlign>,
        val rows: List<List<String>>,
    )

    fun parseMarkdownTable(markdown: String): GfmTable {
        val lines = markdown.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.size >= 2) { "GFM table requires header + delimiter rows" }
        val headers = splitPipeRow(lines[0])
        val delims = splitPipeRow(lines[1])
        require(headers.size == delims.size && headers.isNotEmpty()) { "Header/delimiter column count mismatch" }
        val aligns = delims.map { d ->
            val l = d.startsWith(":")
            val r = d.endsWith(":")
            when {
                l && r -> ColAlign.CENTER
                r -> ColAlign.RIGHT
                else -> ColAlign.LEFT
            }
        }
        val rows = lines.drop(2).map { line ->
            val cells = splitPipeRow(line)
            List(headers.size) { c -> cells.getOrElse(c) { "" } }
        }
        return GfmTable(headers, aligns, rows)
    }

    fun formatPretty(table: GfmTable): String {
        val cols = table.headers.size
        val widths = IntArray(cols) { c ->
            maxOf(
                3,
                table.headers[c].length,
                table.rows.maxOfOrNull { it[c].length } ?: 0,
            )
        }
        fun padCell(s: String, w: Int, align: ColAlign): String = when (align) {
            ColAlign.LEFT -> s.padEnd(w, ' ')
            ColAlign.RIGHT -> s.padStart(w, ' ')
            ColAlign.CENTER -> {
                val rem = w - s.length
                val left = rem / 2
                " ".repeat(left) + s + " ".repeat(rem - left)
            }
        }
        return buildString {
            append("| ")
            append((0 until cols).joinToString(" | ") { c -> padCell(table.headers[c], widths[c], table.alignments[c]) })
            append(" |\n| ")
            append(
                (0 until cols).joinToString(" | ") { c ->
                    val w = widths[c]
                    when (table.alignments[c]) {
                        ColAlign.LEFT -> ":" + "-".repeat(w - 1)
                        ColAlign.RIGHT -> "-".repeat(w - 1) + ":"
                        ColAlign.CENTER -> ":" + "-".repeat((w - 2).coerceAtLeast(1)) + ":"
                    }
                },
            )
            append(" |")
            for (row in table.rows) {
                append("\n| ")
                append((0 until cols).joinToString(" | ") { c -> padCell(row[c], widths[c], table.alignments[c]) })
                append(" |")
            }
        }
    }

    fun toCsv(table: GfmTable): String =
        (listOf(table.headers) + table.rows).joinToString("\n") { row ->
            row.joinToString(",") { cell ->
                if (cell.any { it == ',' || it == '"' || it == '\n' }) "\"${cell.replace("\"", "\"\"")}\"" else cell
            }
        }

    private fun splitPipeRow(line: String): List<String> {
        val inner = line.trim().removePrefix("|").removeSuffix("|")
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < inner.length) {
            if (inner[i] == '\\' && i + 1 < inner.length && inner[i + 1] == '|') {
                cur.append('|'); i += 2
            } else if (inner[i] == '|') {
                out.add(cur.toString().trim()); cur.setLength(0); i++
            } else {
                cur.append(inner[i]); i++
            }
        }
        out.add(cur.toString().trim())
        return out
    }
}
