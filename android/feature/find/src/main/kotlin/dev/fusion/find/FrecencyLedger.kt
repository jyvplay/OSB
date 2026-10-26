// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

import kotlin.math.pow

/**
 * R28 EXECUTED host kernel. Bounded frecency ranking for the Find palette:
 * every visit contributes 0.5^((now - visit) / halfLife), so recent repeats
 * outrank stale history. Ties break by latest visit, then id. Pure ranking;
 * the palette keeps authority over what is shown.
 */
class FrecencyLedger private constructor(private val visits: Map<String, List<Long>>) {
    companion object {
        const val MAX_TARGETS = 64
        const val MAX_VISITS_PER_TARGET = 64
        const val MAX_ID = 128
        const val HALF_LIFE_SECONDS = 14L * 86400L
        fun empty(): FrecencyLedger = FrecencyLedger(emptyMap())
    }

    fun targets(): Set<String> = visits.keys

    /** Record a visit at [atEpoch] (seconds). Evicts the lowest-scoring target when full. */
    fun visit(id: String, atEpoch: Long): FrecencyLedger {
        require(id.length in 1..MAX_ID && id.all { it.code in 0x21..0x7e }) { "Target id must be 1..128 printable ASCII" }
        require(atEpoch in 0..4102444800L) { "atEpoch out of range" }
        val prior = visits[id].orEmpty()
        require(prior.all { it <= atEpoch }) { "Visits must be recorded in non-decreasing time order" }
        val updated = (prior + atEpoch).takeLast(MAX_VISITS_PER_TARGET)
        var next = visits + (id to updated)
        if (next.size > MAX_TARGETS) {
            // Score against the POST-insert map; the pre-insert map would price the new
            // target at zero and evict it unconditionally. (Caught by R28 check 33.)
            val snap = next
            fun s(k: String): Double = snap[k].orEmpty().sumOf {
                0.5.pow((atEpoch - it).coerceAtLeast(0).toDouble() / HALF_LIFE_SECONDS.toDouble())
            }
            fun last(k: String): Long = snap[k]?.lastOrNull() ?: Long.MIN_VALUE
            val victim = snap.keys.minWith(compareBy({ s(it) }, { last(it) }, { it }))
            next = next - victim
        }
        return FrecencyLedger(next)
    }

    fun score(id: String, atEpoch: Long): Double {
        val list = visits[id] ?: return 0.0
        require(atEpoch in 0..4102444800L) { "atEpoch out of range" }
        return list.sumOf { 0.5.pow((atEpoch - it).coerceAtLeast(0).toDouble() / HALF_LIFE_SECONDS.toDouble()) }
    }

    private fun lastVisit(id: String): Long = visits[id]?.lastOrNull() ?: Long.MIN_VALUE

    /** Ranked ids, best first. [limit] 1..MAX_TARGETS. */
    fun rank(atEpoch: Long, limit: Int = MAX_TARGETS): List<String> {
        require(limit in 1..MAX_TARGETS) { "limit out of range" }
        require(atEpoch in 0..4102444800L) { "atEpoch out of range" }
        return visits.keys.sortedWith(
            compareByDescending<String> { score(it, atEpoch) }
                .thenByDescending { lastVisit(it) }
                .thenBy { it }).take(limit)
    }
}
