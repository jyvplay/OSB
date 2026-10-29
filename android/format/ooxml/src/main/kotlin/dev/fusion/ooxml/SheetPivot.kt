// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Clean-room Spreadsheet Pivot Table / OLAP Group-By engine (`ECMA-376 §18.10` conceptual model).
 *
 * Aggregates tabular records across row-dimension keys and optional column-dimension keys using
 * exact [AggFunc] reducers (`SUM`, `COUNT`, `AVERAGE`, `MIN`, `MAX`), conserving row/column
 * marginal totals (`sum(rowTotals) == grandTotal == sum(colTotals)` for `SUM` and `COUNT`).
 */
object SheetPivot {
    const val SCOPE =
        "Deterministic row x column pivot-table aggregator (SUM/COUNT/AVERAGE/MIN/MAX, sorted dimension keys, row/column/grand totals); OLAP MDX cubes and pivotChart XML parts out of scope."

    const val MAX_ROWS = 65_536

    enum class AggFunc { SUM, COUNT, AVERAGE, MIN, MAX }

    data class PivotSpec(
        val rowField: String,
        val colField: String? = null,
        val valueField: String,
        val agg: AggFunc = AggFunc.SUM,
    ) {
        init {
            require(rowField.isNotBlank()) { "rowField must not be blank" }
            require(valueField.isNotBlank()) { "valueField must not be blank" }
            if (colField != null) require(colField.isNotBlank()) { "colField must not be blank" }
        }
    }

    data class PivotResult(
        val rowKeys: List<String>,
        val colKeys: List<String>,
        val matrix: Map<Pair<String, String>, Double?>,
        val rowTotals: Map<String, Double?>,
        val colTotals: Map<String, Double?>,
        val grandTotal: Double?,
    )

    fun summarize(headers: List<String>, rows: List<List<String>>, spec: PivotSpec): PivotResult {
        require(headers.isNotEmpty() && headers.distinct().size == headers.size) {
            "headers must be non-empty and unique"
        }
        require(rows.size <= MAX_ROWS) { "pivot input exceeds $MAX_ROWS rows" }
        val rIdx = headers.indexOf(spec.rowField)
        require(rIdx >= 0) { "unknown rowField '${spec.rowField}'" }
        val cIdx = if (spec.colField != null) {
            val idx = headers.indexOf(spec.colField)
            require(idx >= 0) { "unknown colField '${spec.colField}'" }
            idx
        } else -1
        val vIdx = headers.indexOf(spec.valueField)
        require(vIdx >= 0) { "unknown valueField '${spec.valueField}'" }

        val rowSet = java.util.TreeSet<String>()
        val colSet = java.util.TreeSet<String>()
        val cellBuckets = HashMap<Pair<String, String>, MutableList<Double>>()
        val rowBuckets = HashMap<String, MutableList<Double>>()
        val colBuckets = HashMap<String, MutableList<Double>>()
        val allBucket = ArrayList<Double>()

        var countAll = 0
        val cellCounts = HashMap<Pair<String, String>, Int>()
        val rowCounts = HashMap<String, Int>()
        val colCounts = HashMap<String, Int>()

        for ((lineNo, row) in rows.withIndex()) {
            require(row.size == headers.size) { "row $lineNo has ${row.size} cells, expected ${headers.size}" }
            val rk = row[rIdx].ifBlank { "(blank)" }
            val ck = if (cIdx >= 0) row[cIdx].ifBlank { "(blank)" } else "Value"
            rowSet.add(rk)
            colSet.add(ck)

            val rawVal = row[vIdx].trim()
            if (spec.agg == AggFunc.COUNT) {
                if (rawVal.isNotEmpty()) {
                    countAll++
                    cellCounts[rk to ck] = (cellCounts[rk to ck] ?: 0) + 1
                    rowCounts[rk] = (rowCounts[rk] ?: 0) + 1
                    colCounts[ck] = (colCounts[ck] ?: 0) + 1
                }
            } else if (rawVal.isNotEmpty()) {
                val num = rawVal.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException("non-numeric value '$rawVal' at row $lineNo for ${spec.agg}")
                cellBuckets.getOrPut(rk to ck) { ArrayList() }.add(num)
                rowBuckets.getOrPut(rk) { ArrayList() }.add(num)
                colBuckets.getOrPut(ck) { ArrayList() }.add(num)
                allBucket.add(num)
            }
        }

        if (colSet.isEmpty()) colSet.add("Value")
        val rKeys = rowSet.toList()
        val cKeys = colSet.toList()

        fun reduce(nums: List<Double>?, cnt: Int): Double? = when (spec.agg) {
            AggFunc.COUNT -> cnt.toDouble()
            AggFunc.SUM -> nums?.sum() ?: 0.0
            AggFunc.AVERAGE -> if (nums.isNullOrEmpty()) null else nums.sum() / nums.size
            AggFunc.MIN -> nums?.minOrNull()
            AggFunc.MAX -> nums?.maxOrNull()
        }

        val mat = LinkedHashMap<Pair<String, String>, Double?>()
        for (rk in rKeys) {
            for (ck in cKeys) {
                mat[rk to ck] = reduce(cellBuckets[rk to ck], cellCounts[rk to ck] ?: 0)
            }
        }
        val rTot = rKeys.associateWith { rk -> reduce(rowBuckets[rk], rowCounts[rk] ?: 0) }
        val cTot = cKeys.associateWith { ck -> reduce(colBuckets[ck], colCounts[ck] ?: 0) }
        val gTot = reduce(allBucket, countAll)
        return PivotResult(rKeys, cKeys, mat, rTot, cTot, gTot)
    }
}
