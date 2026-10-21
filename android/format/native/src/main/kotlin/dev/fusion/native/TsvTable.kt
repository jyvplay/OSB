// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

import java.util.Collections

/**
 * Bounded tab-separated value worksheet import. Complements the strict CSV route: tabs-only
 * delimiter, explicit ragged-row refusal, no formula evaluation (leading '=' cells are kept
 * as inert text and flagged), UTF-8 text only. Export writes literal values; nothing here
 * claims spreadsheet-formula equivalence.
 */
object TsvTable {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_ROWS = 4096
    const val MAX_COLS = 256
    const val MAX_CELL = 8192

    data class Table(val rows: List<List<String>>, val formulaLikeCells: List<Pair<Int, Int>>) {
        val rowCount: Int get() = rows.size
        val colCount: Int get() = if (rows.isEmpty()) 0 else rows[0].size
    }

    fun parse(text: String): Table {
        require(text.length <= MAX_BYTES) { "TSV exceeds $MAX_BYTES chars" }
        require('\u0000' !in text) { "NUL bytes refused" }
        val lines = text.split('\n')
        require(lines.size in 1..MAX_ROWS + 1) { "Row count outside 1..$MAX_ROWS" }
        val body = if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
        require(body.isNotEmpty()) { "Empty table" }
        val rows = body.mapIndexed { r, line ->
            require(line.length <= MAX_COLS * (MAX_CELL + 1)) { "Row ${r + 1} too long" }
            require('\r' !in line) { "CR refused at row ${r + 1}; use LF only" }
            val cells = line.split('\t')
            require(cells.size in 1..MAX_COLS) { "Column count outside 1..$MAX_COLS at row ${r + 1}" }
            cells.forEachIndexed { c, cell -> require(cell.length <= MAX_CELL) { "Cell too long at row ${r + 1}, col ${c + 1}" } }
            cells
        }
        val width = rows[0].size
        rows.forEachIndexed { r, row -> require(row.size == width) { "Ragged row ${r + 1}: ${row.size} cells, expected $width" } }
        val flagged = buildList {
            rows.forEachIndexed { r, row -> row.forEachIndexed { c, cell ->
                if (cell.startsWith("=") || cell.startsWith("+") || cell.startsWith("-") || cell.startsWith("@")) add(r to c)
            } }
        }
        return Table(Collections.unmodifiableList(rows.map { Collections.unmodifiableList(it) }),
            Collections.unmodifiableList(flagged))
    }

    /** Literal export: values only, LF rows, no quoting games (tabs/newlines in cells are refused). */
    fun export(table: Table): String {
        require(table.rows.size in 1..MAX_ROWS)
        table.rows.forEachIndexed { r, row ->
            require(row.size == table.colCount) { "Ragged export row $r" }
            row.forEach { require('\t' !in it && '\n' !in it && '\r' !in it) { "Cell needs escaping; export refused" } }
        }
        return table.rows.joinToString("\n") { it.joinToString("\t") } + "\n"
    }

    fun project(table: Table, columns: List<Int>): Table {
        require(columns.isNotEmpty() && columns.size <= MAX_COLS && columns.all { it in 0 until table.colCount })
        require(columns.distinct().size == columns.size) { "Duplicate projection columns" }
        val rows = table.rows.map { row -> columns.map { row[it] } }
        val kept = columns.toSet()
        val flagged = table.formulaLikeCells.mapNotNull { (r, c) ->
            val ni = columns.indexOf(c)
            if (c in kept) r to ni else null
        }
        return Table(Collections.unmodifiableList(rows.map { Collections.unmodifiableList(it) }),
            Collections.unmodifiableList(flagged))
    }
}
