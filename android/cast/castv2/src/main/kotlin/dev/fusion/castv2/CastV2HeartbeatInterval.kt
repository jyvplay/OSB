// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Bounded CastV2 PING/PONG heartbeat interval clamper [1_000, 60_000] ms.
 */
class CastV2HeartbeatInterval private constructor() {
    companion object {
        const val MIN_MS = 1_000L
        const val MAX_MS = 60_000L

        fun clampMs(intervalMs: Long): Long = intervalMs.coerceIn(MIN_MS, MAX_MS)
    }
}
