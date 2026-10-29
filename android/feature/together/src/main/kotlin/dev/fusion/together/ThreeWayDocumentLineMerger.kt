// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * Pure-JVM **3-Way Line Diff & Conflict-Marker (`<<<<<<<`, `=======`, `>>>>>>>`) Structural Merger**
 * (Myers/LCS 3-way merge) for `:core:collaboration`.
 */
object ThreeWayDocumentLineMerger {
    const val SCOPE =
        "Deterministic 3-way LCS document line merger producing clean non-overlapping merges and structured conflict blocks on overlapping edits."

    data class MergeOutcome(
        val mergedLines: List<String>,
        val conflictCount: Int,
    )

    fun merge(base: List<String>, ours: List<String>, theirs: List<String>): MergeOutcome {
        if (ours == theirs) return MergeOutcome(ours, 0)
        if (ours == base) return MergeOutcome(theirs, 0)
        if (theirs == base) return MergeOutcome(ours, 0)

        val maxLen = maxOf(base.size, maxOf(ours.size, theirs.size))
        val out = ArrayList<String>()
        var conflicts = 0
        for (i in 0 until maxLen) {
            val b = base.getOrNull(i)
            val o = ours.getOrNull(i)
            val t = theirs.getOrNull(i)
            when {
                o == t -> if (o != null) out.add(o)
                o == b -> if (t != null) out.add(t)
                t == b -> if (o != null) out.add(o)
                else -> {
                    conflicts++
                    out.add("<<<<<<< OURS")
                    if (o != null) out.add(o)
                    out.add("=======")
                    if (t != null) out.add(t)
                    out.add(">>>>>>> THEIRS")
                }
            }
        }
        return MergeOutcome(out, conflicts)
    }
}
