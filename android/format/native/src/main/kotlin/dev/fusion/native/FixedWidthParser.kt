package dev.fusion.native

/**
 * Bounded fixed-width text parser.
 *
 * Parses fixed-width records defined by column widths. Each record must
 * exactly match the total width (no short or long lines accepted unless
 * they are blank separators).
 *
 * Bounds (per call):
 *   - text length <= 65,536 UTF-16 code units
 *   - columns <= 256
 *   - rows <= 10,000
 *   - column width <= 4,096 UTF-16 code units
 *   - total width <= 65,536 UTF-16 code units
 *
 * Never trims cell content by default. Optional trim parameter controls
 * whether leading/trailing whitespace is removed from each cell.
 */
object FixedWidthParser {
    private const val MAX_TEXT = 65_536
    private const val MAX_COLUMNS = 256
    private const val MAX_ROWS = 10_000
    private const val MAX_COLUMN_WIDTH = 4_096
    private const val MAX_TOTAL_WIDTH = 65_536

    data class Column(
        val name: String,
        val width: Int
    )

    data class Schema(
        val columns: List<Column>
    ) {
        val totalWidth: Int get = columns.sumOf { it.width }
    }

    data class Row(
        val values: List<String>
    )

    data class Table(
        val schema: Schema,
        val rows: List<Row>
    )

    fun parse(text: String, schema: Schema, trim: Boolean = false): Table {
        require(text.length <= MAX_TEXT) { "Fixed-width text exceeds $MAX_TEXT UTF-16 units" }
        require(schema.columns.size <= MAX_COLUMNS) { "Too many columns (max $MAX_COLUMNS)" }
        require(schema.columns.isNotEmpty()) { "Schema must define at least one column" }
        for (col in schema.columns) {
            require(col.width in 1..MAX_COLUMN_WIDTH) {
                "Column '${col.name}' width ${col.width} outside [1, $MAX_COLUMN_WIDTH]"
            }
            require(col.name.length <= 128) { "Column name too long" }
        }
        require(schema.totalWidth <= MAX_TOTAL_WIDTH) {
            "Total width ${schema.totalWidth} exceeds $MAX_TOTAL_WIDTH"
        }

        val width = schema.totalWidth
        val rows = mutableListOf<Row>()

        for (line in text.lines()) {
            if (line.isBlank()) continue
            require(line.length == width) {
                "Line length ${line.length} does not match schema width $width"
            }

            val values = mutableListOf<String>()
            var pos = 0
            for (col in schema.columns) {
                val cell = line.substring(pos, pos + col.width)
                values.add(if (trim) cell.trim() else cell)
                pos += col.width
            }
            require(values.size <= MAX_COLUMNS) { "Too many columns in row" }
            rows.add(Row(values))
        }

        require(rows.size <= MAX_ROWS) { "Too many rows (max $MAX_ROWS)" }

        return Table(schema, rows.toList())
    }
}
