// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * Lamport causal clock for collaboration events. Distinct from [PeerClock]'s
 * wall-clock offset bounds: this counter orders causally related operations
 * (local tick on send, max-plus-one on receive) without any claim about
 * real time. Two events with equal stamps are concurrent-or-equal, never
 * ordered — callers needing total order must break ties with a sender id.
 * The counter saturates by refusal at MAX rather than wrapping, so a wrap
 * can never reorder history.
 */
class LamportClock private constructor(private var value: Long) {
    companion object {
        const val MAX = Long.MAX_VALUE - 1

        fun fresh(): LamportClock = LamportClock(0)
    }

    fun read(): Long = value

    fun tick(): Long {
        require(value < MAX) { "lamport counter saturated" }
        return ++value
    }

    fun send(): Long = tick()

    fun receive(remote: Long): Long {
        require(remote in 0..MAX)
        val next = maxOf(value, remote)
        require(next < MAX) { "lamport counter saturated" }
        value = next + 1
        return value
    }
}
