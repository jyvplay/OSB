// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Pure-JVM **Hybrid Logical Clock (`HLC`, Kulkarni et al. 2014) Cross-Device Continuity Event
 * Sequencer** for `:feature:continuity`, guaranteeing causal monotonicity even under physical
 * wall-clock skew across phone, tablet, and desktop sessions.
 */
object ContinuityHybridLogicalClock {
    const val SCOPE =
        "Kulkarni et al. Hybrid Logical Clock (HLC) causal timestamp generator and bounded physical-skew validator for cross-device handoff."

    const val MAX_ALLOWED_DRIFT_MS = 60_000L

    data class HlcTimestamp(
        val logicalWallMs: Long,
        val counter: Int,
        val nodeId: String,
    ) : Comparable<HlcTimestamp> {
        override fun compareTo(other: HlcTimestamp): Int =
            compareValuesBy(this, other, HlcTimestamp::logicalWallMs, HlcTimestamp::counter, HlcTimestamp::nodeId)
    }

    fun sendOrLocalEvent(lastLocal: HlcTimestamp, physicalNowMs: Long): HlcTimestamp {
        val nextWall = maxOf(lastLocal.logicalWallMs, physicalNowMs)
        val nextCounter = if (nextWall == lastLocal.logicalWallMs) lastLocal.counter + 1 else 0
        return HlcTimestamp(nextWall, nextCounter, lastLocal.nodeId)
    }

    fun receiveRemoteEvent(lastLocal: HlcTimestamp, remote: HlcTimestamp, physicalNowMs: Long): HlcTimestamp {
        require(remote.logicalWallMs - physicalNowMs <= MAX_ALLOWED_DRIFT_MS) {
            "Remote HLC skew (${remote.logicalWallMs - physicalNowMs} ms) exceeds MAX_ALLOWED_DRIFT_MS"
        }
        val nextWall = maxOf(lastLocal.logicalWallMs, maxOf(remote.logicalWallMs, physicalNowMs))
        val nextCounter = when {
            nextWall == lastLocal.logicalWallMs && nextWall == remote.logicalWallMs ->
                maxOf(lastLocal.counter, remote.counter) + 1
            nextWall == lastLocal.logicalWallMs -> lastLocal.counter + 1
            nextWall == remote.logicalWallMs -> remote.counter + 1
            else -> 0
        }
        return HlcTimestamp(nextWall, nextCounter, lastLocal.nodeId)
    }
}
