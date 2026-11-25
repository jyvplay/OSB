// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Bounded capture bitrate clamper (bps).
 */
class CaptureBitrateClamper private constructor() {
    companion object {
        fun clamp(bps: Long, minBps: Long = 64_000L, maxBps: Long = 50_000_000L): Long {
            require(minBps in 1L..maxBps) { "invalid bitrate bounds" }
            return bps.coerceIn(minBps, maxBps)
        }
    }
}
