// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Bounded sliding-window event counter over `(nowMs - windowMs, nowMs]`.
 */
class SlidingWindowRpsCounter private constructor(private val windowMs: Long) {
    companion object {
        const val MAX_EVENTS = 10_000
        const val MAX_WINDOW_MS = 3_600_000L

        fun create(windowMs: Long): SlidingWindowRpsCounter {
            require(windowMs in 1L..MAX_WINDOW_MS) { "windowMs out of bounds" }
            return SlidingWindowRpsCounter(windowMs)
        }
    }

    private val timestamps = ArrayDeque<Long>()

    fun record(nowMs: Long) {
        require(nowMs >= 0L) { "negative nowMs" }
        prune(nowMs)
        if (timestamps.size < MAX_EVENTS) {
            timestamps.addLast(nowMs)
        }
    }

    fun count(nowMs: Long): Int {
        require(nowMs >= 0L) { "negative nowMs" }
        prune(nowMs)
        return timestamps.size
    }

    private fun prune(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (timestamps.isNotEmpty() && timestamps.first() <= cutoff) {
            timestamps.removeFirst()
        }
    }
}
