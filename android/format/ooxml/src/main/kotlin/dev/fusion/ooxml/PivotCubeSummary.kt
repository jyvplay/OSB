// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Clean-room Spreadsheet **Pivot Table Multi-Axis Aggregation Cube** (`rows x cols -> AggOp`).
 *
 * Groups tabular records by row key and column key, computes exact `SUM`, `COUNT`, `AVERAGE`,
 * `MIN`, or `MAX` cells plus row subtotals, column subtotals, and the grand total in deterministic
 * sorted key order.
 */
object PivotCubeSummary {
    const val SCOPE =
        "Deterministic spreadsheet Pivot Table 2D aggregation cube (SUM, COUNT, AVERAGE, MIN, MAX with sorted row/column headers, subtotals, and grand total)."

    enum class AggOp { SUM, COUNT, AVERAGE, MIN, MAX }

    data class Record(
        val rowKey: String,
        val colKey: String,
        val value: Double,
    ) {
        init {
            require(value.isFinite()) { "non-finite pivot value $value" }
        }
    }

    data class PivotTable(
        val rowKeys: List<String>,
        val colKeys: List<String>,
        val cells: Map<Pair<String, String>, Double>,
        val rowTotals: Map<String, Double>,
        val colTotals: Map<String, Double>,
        val grandTotal: Double,
    )

    fun build(records: List<Record>, op: AggOp): PivotTable {
        val rowKeys = records.map { it.rowKey }.distinct().sorted()
        val colKeys = records.map { it.colKey }.distinct().sorted()
        val byCell = LinkedHashMap<Pair<String, String>, MutableList<Double>>()
        val byRow = LinkedHashMap<String, MutableList<Double>>()
        val byCol = LinkedHashMap<String, MutableList<Double>>()
        val all = ArrayList<Double>(records.size)

        for (r in records) {
            byCell.getOrPut(r.rowKey to r.colKey) { ArrayList() }.add(r.value)
            byRow.getOrPut(r.rowKey) { ArrayList() }.add(r.value)
            byCol.getOrPut(r.colKey) { ArrayList() }.add(r.value)
            all.add(r.value)
        }

        val cells = LinkedHashMap<Pair<String, String>, Double>()
        for (rk in rowKeys) {
            for (ck in colKeys) {
                val bucket = byCell[rk to ck]
                if (!bucket.isNullOrEmpty()) cells[rk to ck] = reduce(bucket, op)
            }
        }
        val rowTotals = rowKeys.associateWith { rk -> reduce(byRow[rk].orEmpty(), op) }
        val colTotals = colKeys.associateWith { ck -> reduce(byCol[ck].orEmpty(), op) }
        val grand = if (all.isEmpty()) 0.0 else reduce(all, op)
        return PivotTable(rowKeys, colKeys, cells, rowTotals, colTotals, grand)
    }

    private fun reduce(vals: List<Double>, op: AggOp): Double {
        if (vals.isEmpty()) return 0.0
        return when (op) {
            AggOp.SUM -> vals.sum()
            AggOp.COUNT -> vals.size.toDouble()
            AggOp.AVERAGE -> vals.sum() / vals.size
            AggOp.MIN -> vals.min()
            AggOp.MAX -> vals.max()
        }
    }
}
