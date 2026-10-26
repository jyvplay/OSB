// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/** Causal order between two vector clocks. */
enum class ClockOrder { BEFORE, AFTER, EQUAL, CONCURRENT }

/**
 * Vector clocks over caller-supplied counter maps: increment, elementwise
 * -max merge, and BEFORE/AFTER/EQUAL/CONCURRENT comparison (missing keys
 * read as zero). Outputs are canonical (no zero entries). Scalar time
 * stays with [LamportClock], offset bounds with [PeerClock]; transport
 * and conflict resolution are outside this kernel. Counter overflow
 * (Long.MAX + 1) is refused, never wrapped. Caps: 64 nodes.
 */
object VectorClocks {
    const val MAX_NODES = 64
    private val NODE = Regex("[A-Za-z0-9._\\-]{1,64}")

    private fun valid(clock: Map<String, Long>, label: String): Map<String, Long> {
        require(clock.size <= MAX_NODES) { "$label over $MAX_NODES nodes" }
        for ((k, v) in clock) {
            require(NODE.matches(k)) { "$label bad node $k" }
            require(v >= 0) { "$label negative counter" }
        }
        return clock
    }

    fun increment(clock: Map<String, Long>, node: String): Map<String, Long> {
        valid(clock, "clock")
        require(NODE.matches(node)) { "bad node $node" }
        val next = clock.toMutableMap()
        val cur = next.getOrDefault(node, 0L)
        require(cur < Long.MAX_VALUE) { "counter overflow" }
        require(next.size < MAX_NODES || node in next) { "over $MAX_NODES nodes" }
        next[node] = cur + 1
        return next
    }

    /** Elementwise max over the key union, zeros dropped. */
    fun merge(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> {
        valid(a, "a")
        valid(b, "b")
        val out = mutableMapOf<String, Long>()
        for (k in a.keys + b.keys) {
            val m = maxOf(a.getOrDefault(k, 0L), b.getOrDefault(k, 0L))
            if (m > 0) out[k] = m
        }
        require(out.size <= MAX_NODES) { "merge over $MAX_NODES nodes" }
        return out
    }

    fun compare(a: Map<String, Long>, b: Map<String, Long>): ClockOrder {
        valid(a, "a")
        valid(b, "b")
        var le = true
        var ge = true
        for (k in a.keys + b.keys) {
            val x = a.getOrDefault(k, 0L)
            val y = b.getOrDefault(k, 0L)
            if (x > y) le = false
            if (x < y) ge = false
        }
        return when {
            le && ge -> ClockOrder.EQUAL
            le -> ClockOrder.BEFORE
            ge -> ClockOrder.AFTER
            else -> ClockOrder.CONCURRENT
        }
    }
}
