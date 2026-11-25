// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Bounded terminal BEL (\x07) rate-limiter.
 */
class TerminalBellThrottle private constructor(private val minIntervalMs: Long) {
    companion object {
        fun create(minIntervalMs: Long): TerminalBellThrottle {
            require(minIntervalMs >= 0L) { "negative minIntervalMs" }
            return TerminalBellThrottle(minIntervalMs)
        }
    }

    private var lastBellMs: Long = -1L

    fun shouldRing(nowMs: Long): Boolean {
        require(nowMs >= 0L) { "negative nowMs" }
        if (lastBellMs >= 0L && nowMs - lastBellMs < minIntervalMs) return false
        lastBellMs = nowMs
        return true
    }
}
