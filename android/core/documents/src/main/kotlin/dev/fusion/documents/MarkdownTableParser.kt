package dev.fusion.documents

/**
 * Bounded Markdown pipe-table parser (CommonMark subset).
 *
 * Parses tables of the form:
 *   | Header 1 | Header 2 |
 *   | -------- | -------- |
 *   | Cell 1   | Cell 2   |
 *
 * Bounds (per call, not whole-process):
 *   - text length <= 65,536 UTF-16 code units
 *   - columns per row <= 256
 *   - rows <= 10,000
 *   - cell content <= 65,536 UTF-16 code units
 *
 * Never trims, drops blanks, or evaluates formulas. Returns the header row
 * and data rows as separate lists. A row with the wrong column count is a parse error.
 */
object MarkdownTableParser {
    private const val MAX_TEXT = 65_536
    private const val MAX_COLUMNS = 256
    private const val MAX_ROWS = 10_000
    private const val MAX_CELL = 65_536

    data class Table(
        val header: List<String>,
        val rows: List<List<String>>
    )

    fun parse(text: String): Table {
        require(text.length <= MAX_TEXT) { "Markdown table text exceeds $MAX_TEXT UTF-16 units" }
        if (text.isBlank()) {
            return Table(emptyList(), emptyList())
        }

        val lines = text.lines()
        var header: List<String> = emptyList()
        val rows = mutableListOf<List<String>>()
        var inTable = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                inTable = false
                continue
            }

            if (trimmed.startsWith("|") && trimmed.contains("|")) {
                val cells = splitRow(trimmed)
                if (isSeparatorRow(cells)) {
                    inTable = true
                    continue
                }
                if (inTable) {
                    require(cells.size <= MAX_COLUMNS) { "Too many CSV columns" }
                    require(cells.size == header.size) {
                        "Row has ${cells.size} columns, expected ${header.size}"
                    }
                    require(rows.size < MAX_ROWS) { "Too many table rows" }
                    rows.add(cells)
                } else {
                    require(cells.size <= MAX_COLUMNS) { "Too many table columns" }
                    header = cells
                    inTable = true
                }
            } else {
                inTable = false
            }
        }

        return Table(header, rows.toList())
    }

    private fun splitRow(line: String): List<String> {
        // Strip leading/trailing pipes
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|")) s = s.substring(0, s.length - 1)

        val cells = mutableListOf<String>()
        var cell = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i++]
            if (c == '|') {
                cells.add(cell.toString())
                cell.setLength(0)
            } else {
                require(cell.length < MAX_CELL) { "Table cell exceeds $MAX_CELL UTF-16 units" }
                cell.append(c)
            }
        }
        cells.add(cell.toString())
        return cells
    }

    private fun isSeparatorRow(cells: List<String>): Boolean {
        return cells.all { c ->
            val t = c.trim()
            t.isNotEmpty() && t.all { it == '-' || it == ':' }
        }
    }
}
