package dev.fusion.ooxml

/**
 * Hidden row/column RUN model (ECMA-376 row/col elements carry
 * hidden="1"; files store consecutive hidden indices efficiently and
 * consumers need run arithmetic, not per-index flags).
 *
 * Runs are half-open [start, endExclusive) over 1-based indices,
 * normalized via merge (the SpanMerge law restated for the grid);
 * [isHidden], [hiddenCount] and [nextVisible] are the three queries
 * the grid renderer needs.
 *
 * Declared narrowings: visibility only — outline levels and grouping
 * (the collapse UI) are a separate model; runs beyond the grid refuse.
 */
object SheetHide {
    const val SCOPE = "Hidden-run arithmetic over the grid; outline/grouping is a separate model."

    data class Run(val start: Int, val endExclusive: Int) {
        init {
            require(start >= 1 && endExclusive > start) { "bad run [$start,$endExclusive)" }
            require(endExclusive <= SheetRange.MAX_ROW + 1) { "run beyond the grid" }
        }
    }

    fun normalize(runs: List<Run>): List<Run> {
        if (runs.isEmpty()) return emptyList()
        val sorted = runs.sortedBy { it.start }
        val out = ArrayList<Run>()
        var cur = sorted[0]
        for (r in sorted.drop(1)) {
            cur = if (r.start <= cur.endExclusive) {
                if (r.endExclusive > cur.endExclusive) Run(cur.start, r.endExclusive) else cur
            } else { out.add(cur); r }
        }
        out.add(cur)
        return out
    }

    fun isHidden(runs: List<Run>, index: Int): Boolean {
        require(index >= 1) { "1-based indices" }
        return normalize(runs).any { index >= it.start && index < it.endExclusive }
    }

    fun hiddenCount(runs: List<Run>): Int = normalize(runs).sumOf { it.endExclusive - it.start }

    /** First visible index at or after [from]. */
    fun nextVisible(runs: List<Run>, from: Int): Int {
        require(from >= 1) { "1-based indices" }
        var i = from
        for (r in normalize(runs)) {
            if (i < r.start) return i
            if (i < r.endExclusive) i = r.endExclusive
        }
        require(i <= SheetRange.MAX_ROW) { "no visible index remains in the grid" }
        return i
    }
}
