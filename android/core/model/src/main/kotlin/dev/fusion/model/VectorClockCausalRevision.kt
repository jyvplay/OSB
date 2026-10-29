package dev.fusion.model

/**
 * Clean-room Bounded Vector Clock Causal Ordering & Split-Brain Conflict Detector (Apache-2.0).
 */
object VectorClockCausalRevision {
    const val SCOPE =
        "Distributed bounded vector-clock causal ordering (BEFORE / AFTER / EQUAL / CONCURRENT_CONFLICT) & merge clock"

    enum class CausalRelation { EQUAL, HAPPENED_BEFORE, HAPPENED_AFTER, CONCURRENT_CONFLICT }

    data class VectorClock(val entries: Map<String, Long> = emptyMap()) {
        fun increment(replicaId: String): VectorClock {
            require(replicaId.isNotBlank())
            val next = LinkedHashMap(entries)
            next[replicaId] = (entries[replicaId] ?: 0L) + 1L
            return VectorClock(next.toSortedMap())
        }

        fun merge(other: VectorClock): VectorClock {
            val keys = (entries.keys + other.entries.keys).toSortedSet()
            return VectorClock(keys.associateWith { k -> maxOf(entries[k] ?: 0L, other.entries[k] ?: 0L) })
        }

        fun compareToCausal(other: VectorClock): CausalRelation {
            val keys = entries.keys + other.entries.keys
            var strictlyLess = false
            var strictlyGreater = false
            for (k in keys) {
                val a = entries[k] ?: 0L
                val b = other.entries[k] ?: 0L
                if (a < b) strictlyLess = true
                if (a > b) strictlyGreater = true
            }
            return when {
                !strictlyLess && !strictlyGreater -> CausalRelation.EQUAL
                strictlyLess && !strictlyGreater -> CausalRelation.HAPPENED_BEFORE
                strictlyGreater && !strictlyLess -> CausalRelation.HAPPENED_AFTER
                else -> CausalRelation.CONCURRENT_CONFLICT
            }
        }
    }
}
