package dev.fusion.calc

/**
 * Clean-room OLAP Cross-Filter Slicer & Calculated-Measure Pivot cube engine (Apache-2.0).
 *
 * Computes interactive Slicer button states (`selected` vs `available` vs `crossFilteredOut`
 * when other dimensions filter out a member!) plus `% of Grand Total`, `% of Row Total`,
 * and `Running Total` calculated measures.
 */
object PivotSlicerCrossFilterEngine {
    const val SCOPE =
        "Multi-dimensional OLAP cross-filter Slicer engine + % of Grand Total / % of Row / Running Total calculated pivot measures"

    enum class CalculatedShowAs { RAW_SUM, PERCENT_OF_GRAND_TOTAL, PERCENT_OF_ROW_TOTAL, RUNNING_TOTAL_BY_ROW }

    data class SlicerButtonState(
        val member: String,
        val selected: Boolean,
        val hasMatchingFactsUnderOtherSlicers: Boolean,
    )

    data class CrossFilteredPivotResult(
        val rowKeys: List<String>,
        val colKeys: List<String>,
        val matrix: List<List<Double>>,
        val rowTotals: List<Double>,
        val colTotals: List<Double>,
        val grandTotal: Double,
        val slicerStates: Map<String, List<SlicerButtonState>>,
    )

    fun evaluateCube(
        records: List<Map<String, String>>,
        measureField: String,
        rowDimension: String,
        colDimension: String,
        activeSlicers: Map<String, Set<String>> = emptyMap(),
        showAs: CalculatedShowAs = CalculatedShowAs.RAW_SUM,
    ): CrossFilteredPivotResult {
        require(records.isNotEmpty()) { "Pivot cube requires >= 1 record" }

        // Compute slicer button availability: for dimension D, evaluate against all active slicers EXCEPT D!
        val allSlicerDims = (activeSlicers.keys + listOf(rowDimension, colDimension)).distinct()
        val slicerStates = LinkedHashMap<String, List<SlicerButtonState>>()
        for (dim in allSlicerDims) {
            val allMembers = records.mapNotNull { it[dim] }.distinct().sorted()
            val otherFilters = activeSlicers.filterKeys { it != dim }
            val availableUnderOthers = records
                .filter { rec -> matchesAll(rec, otherFilters) }
                .mapNotNull { it[dim] }
                .toHashSet()
            val selectedSet = activeSlicers[dim]
            slicerStates[dim] = allMembers.map { m ->
                SlicerButtonState(
                    member = m,
                    selected = selectedSet == null || m in selectedSet,
                    hasMatchingFactsUnderOtherSlicers = m in availableUnderOthers,
                )
            }
        }

        val filtered = records.filter { matchesAll(it, activeSlicers) }
        val rowKeys = filtered.mapNotNull { it[rowDimension] }.distinct().sorted()
        val colKeys = filtered.mapNotNull { it[colDimension] }.distinct().sorted()
        val raw = Array(rowKeys.size) { DoubleArray(colKeys.size) }
        val rIndex = rowKeys.withIndex().associate { it.value to it.index }
        val cIndex = colKeys.withIndex().associate { it.value to it.index }

        for (rec in filtered) {
            val r = rIndex[rec[rowDimension]] ?: continue
            val c = cIndex[rec[colDimension]] ?: continue
            val v = rec[measureField]?.toDoubleOrNull() ?: 0.0
            raw[r][c] += v
        }

        val rowTotals = raw.map { it.sum() }
        val colTotals = List(colKeys.size) { c -> raw.indices.sumOf { raw[it][c] } }
        val grandTotal = rowTotals.sum()

        val transformed = List(rowKeys.size) { r ->
            var running = 0.0
            List(colKeys.size) { c ->
                val v = raw[r][c]
                when (showAs) {
                    CalculatedShowAs.RAW_SUM -> v
                    CalculatedShowAs.PERCENT_OF_GRAND_TOTAL -> if (grandTotal == 0.0) 0.0 else v / grandTotal
                    CalculatedShowAs.PERCENT_OF_ROW_TOTAL -> if (rowTotals[r] == 0.0) 0.0 else v / rowTotals[r]
                    CalculatedShowAs.RUNNING_TOTAL_BY_ROW -> {
                        running += v
                        running
                    }
                }
            }
        }
        return CrossFilteredPivotResult(
            rowKeys = rowKeys,
            colKeys = colKeys,
            matrix = transformed,
            rowTotals = rowTotals,
            colTotals = colTotals,
            grandTotal = grandTotal,
            slicerStates = slicerStates,
        )
    }

    private fun matchesAll(rec: Map<String, String>, slicers: Map<String, Set<String>>): Boolean =
        slicers.all { (dim, allowed) -> allowed.isEmpty() || rec[dim] in allowed }
}
