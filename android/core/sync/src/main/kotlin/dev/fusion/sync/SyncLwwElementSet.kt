// SPDX-License-Identifier: Apache-2.0
package dev.fusion.sync

/**
 * Production-grade CRDT LWW-Element-Set (remove-wins on tie) &
 * Vector-Clock causality comparator.
 */
class SyncLwwElementSet<T> private constructor() {
    companion object {
        enum class CausalOrder { BEFORE, AFTER, EQUAL, CONCURRENT }

        fun <T> create(): SyncLwwElementSet<T> = SyncLwwElementSet()

        fun compareVectorClocks(a: Map<String, Long>, b: Map<String, Long>): CausalOrder {
            val keys = a.keys + b.keys
            var aLess = false
            var bLess = false
            for (k in keys) {
                val va = a[k] ?: 0L
                val vb = b[k] ?: 0L
                if (va < vb) aLess = true
                if (vb < va) bLess = true
            }
            return when {
                !aLess && !bLess -> CausalOrder.EQUAL
                aLess && !bLess -> CausalOrder.BEFORE
                !aLess && bLess -> CausalOrder.AFTER
                else -> CausalOrder.CONCURRENT
            }
        }
    }

    private val addSet = HashMap<T, Long>()
    private val removeSet = HashMap<T, Long>()

    fun add(element: T, timestampMs: Long) {
        val prev = addSet[element]
        if (prev == null || timestampMs > prev) addSet[element] = timestampMs
    }

    fun remove(element: T, timestampMs: Long) {
        val prev = removeSet[element]
        if (prev == null || timestampMs > prev) removeSet[element] = timestampMs
    }

    fun contains(element: T): Boolean {
        val a = addSet[element] ?: return false
        val r = removeSet[element] ?: return true
        return a > r // remove-wins on tie
    }

    fun merge(other: SyncLwwElementSet<T>) {
        for ((k, ts) in other.addSet) add(k, ts)
        for ((k, ts) in other.removeSet) remove(k, ts)
    }

    fun elements(): Set<T> = addSet.keys.filter { contains(it) }.toSet()
}
