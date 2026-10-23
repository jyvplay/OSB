// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Reciprocal-rank fusion over caller-ranked id lists.
 *
 * Sits next to [FuzzyScore] (which scores one candidate) and
 * [FrecencyLedger] (decayed ranking): neither merges several ranked
 * lists. [fuse] scores each id as Σ 1/(k+rank) across the rankings
 * that contain it (ranks 1-based, default k = 60 per the RRF paper)
 * and sorts score-descending; ties keep first-seen order (stable and
 * documented, not relevance). Duplicate ids inside ONE ranking are
 * refused (a ranking must rank); membership and scoring stay caller
 * duty — this kernel only fuses.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object RankFuse {
    const val MAX_LISTS = 100
    const val MAX_IDS = 10_000

    /** A fused id with its RRF score. */
    data class Scored(val id: String, val score: Double)

    /**
     * Fuses [rankings] with constant [k].
     *
     * @throws IllegalArgumentException on a bad k, an empty/over-cap
     * list set, blank ids, or a duplicated id inside one ranking.
     */
    fun fuse(rankings: List<List<String>>, k: Int = 60): List<Scored> {
        require(k >= 1) { "bad k" }
        require(rankings.isNotEmpty() && rankings.size <= MAX_LISTS) { "bad list count" }
        val scores = linkedMapOf<String, Double>()
        for (list in rankings) {
            require(list.size <= MAX_IDS) { "list over cap" }
            val seen = hashSetOf<String>()
            for ((rank, id) in list.withIndex()) {
                require(id.isNotBlank()) { "blank id" }
                require(seen.add(id)) { "dup id in ranking" }
                scores[id] = (scores[id] ?: 0.0) + 1.0 / (k + rank + 1)
            }
        }
        return scores.entries.sortedByDescending { it.value }.map { Scored(it.key, it.value) }
    }
}
