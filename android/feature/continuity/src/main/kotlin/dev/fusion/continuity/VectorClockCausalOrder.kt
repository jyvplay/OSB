// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Distributed **Vector Clock Causal Partial-Order Comparator & Join-Semilattice Merger**.
 *
 * Classifies causal history across multi-device Continuity replicas (`EQUAL`, `HAPPENS_BEFORE`,
 * `HAPPENS_AFTER`, `CONCURRENT_CONFLICT`).
 */
object VectorClockCausalOrder {
    const val SCOPE =
        "Distributed Vector Clock increment, join-semilattice merge, and causal partial-order comparator (EQUAL, HAPPENS_BEFORE, HAPPENS_AFTER, CONCURRENT_CONFLICT)."

    enum class Relation { EQUAL, HAPPENS_BEFORE, HAPPENS_AFTER, CONCURRENT_CONFLICT }

    data class VectorClock(val entries: Map<String, Long> = emptyMap()) {
        init {
            require(entries.all { it.key.isNotBlank() && it.value >= 0L }) { "invalid vector clock entry" }
        }

        fun tick(nodeId: String): VectorClock {
            require(nodeId.isNotBlank()) { "blank nodeId" }
            val next = LinkedHashMap(entries)
            next[nodeId] = (next[nodeId] ?: 0L) + 1L
            return VectorClock(next.filterValues { it > 0L })
        }

        fun merge(other: VectorClock): VectorClock {
            val keys = (entries.keys + other.entries.keys).sorted()
            val merged = LinkedHashMap<String, Long>()
            for (k in keys) {
                val m = maxOf(entries[k] ?: 0L, other.entries[k] ?: 0L)
                if (m > 0L) merged[k] = m
            }
            return VectorClock(merged)
        }
    }

    fun compare(a: VectorClock, b: VectorClock): Relation {
        val keys = a.entries.keys + b.entries.keys
        var aLess = false
        var bLess = false
        for (k in keys) {
            val av = a.entries[k] ?: 0L
            val bv = b.entries[k] ?: 0L
            if (av < bv) aLess = true
            if (bv < av) bLess = true
        }
        return when {
            !aLess && !bLess -> Relation.EQUAL
            aLess && !bLess -> Relation.HAPPENS_BEFORE
            bLess && !aLess -> Relation.HAPPENS_AFTER
            else -> Relation.CONCURRENT_CONFLICT
        }
    }
}
