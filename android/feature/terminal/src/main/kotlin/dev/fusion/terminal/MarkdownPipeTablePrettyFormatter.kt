// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Pure-JVM **GitHub-Flavored Markdown (`GFM`) Pipe-Table Column-Alignment & Monospace Pretty
 * Formatter** for `:feature:docs`.
 */
object MarkdownPipeTablePrettyFormatter {
    const val SCOPE =
        "GFM pipe-table (:---, :---:, ---:) parser, cell-width calculator, and deterministic aligned ASCII/Unicode pretty-printer."

    enum class ColAlign { LEFT, CENTER, RIGHT }

    fun formatTable(rawLines: List<String>): List<String> {
        require(rawLines.size >= 2) { "Markdown table requires header + delimiter rows" }
        val rows = rawLines.map { parseRow(it) }
        val colCount = rows.maxOf { it.size }
        require(colCount >= 1)
        val aligns = (0 until colCount).map { c ->
            val spec = rows[1].getOrNull(c)?.trim().orEmpty()
            when {
                spec.startsWith(":") && spec.endsWith(":") -> ColAlign.CENTER
                spec.endsWith(":") -> ColAlign.RIGHT
                else -> ColAlign.LEFT
            }
        }
        val dataRows = listOf(rows[0]) + rows.drop(2)
        val widths = (0 until colCount).map { c ->
            dataRows.maxOf { r -> (r.getOrNull(c)?.length ?: 0) }.coerceAtLeast(3)
        }
        val out = ArrayList<String>(rawLines.size)
        out.add(renderDataRow(rows[0], widths, aligns))
        out.add(renderDelimiterRow(widths, aligns))
        for (r in rows.drop(2)) out.add(renderDataRow(r, widths, aligns))
        return out
    }

    private fun parseRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    private fun renderDelimiterRow(widths: List<Int>, aligns: List<ColAlign>): String =
        widths.indices.joinToString(" | ", prefix = "| ", postfix = " |") { c ->
            val w = widths[c]
            when (aligns[c]) {
                ColAlign.LEFT -> ":" + "-".repeat(w - 1)
                ColAlign.RIGHT -> "-".repeat(w - 1) + ":"
                ColAlign.CENTER -> ":" + "-".repeat((w - 2).coerceAtLeast(1)) + ":"
            }
        }

    private fun renderDataRow(cells: List<String>, widths: List<Int>, aligns: List<ColAlign>): String =
        widths.indices.joinToString(" | ", prefix = "| ", postfix = " |") { c ->
            val s = cells.getOrNull(c).orEmpty()
            val pad = (widths[c] - s.length).coerceAtLeast(0)
            when (aligns[c]) {
                ColAlign.LEFT -> s + " ".repeat(pad)
                ColAlign.RIGHT -> " ".repeat(pad) + s
                ColAlign.CENTER -> {
                    val lp = pad / 2
                    " ".repeat(lp) + s + " ".repeat(pad - lp)
                }
            }
        }
}
