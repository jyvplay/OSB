// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * RAOP / AirPlay Audio **NTP-Style 4-Timestamp (`t1, t2, t3, t4`) Clock Offset, Round-Trip Delay,
 * and 44.1 / 48 kHz RTP Sample-Clock Drift Compensator** (`θ = ((t2 - t1) + (t3 - t4)) / 2`,
 * `δ = (t4 - t1) - (t3 - t2)`).
 */
object RaopRtpTimingSyncCalculator {
    const val SCOPE =
        "RAOP/AirPlay 4-timestamp NTP timing sync (clock offset + RTT delay) and audio sample-frame skew compensator."

    data class TimingSyncResult(
        val clockOffsetMicros: Long,
        val roundTripDelayMicros: Long,
        val sampleSkewFrames: Long,
        val requiresResampleNudge: Boolean,
    )

    fun evaluateTimingExchange(
        t1ClientSendMicros: Long,
        t2ServerRecvMicros: Long,
        t3ServerSendMicros: Long,
        t4ClientRecvMicros: Long,
        sampleRateHz: Int = 44_100,
    ): TimingSyncResult {
        require(t4ClientRecvMicros >= t1ClientSendMicros && t3ServerSendMicros >= t2ServerRecvMicros)
        require(sampleRateHz in setOf(44_100, 48_000))
        val offset = ((t2ServerRecvMicros - t1ClientSendMicros) + (t3ServerSendMicros - t4ClientRecvMicros)) / 2L
        val rtt = (t4ClientRecvMicros - t1ClientSendMicros) - (t3ServerSendMicros - t2ServerRecvMicros)
        val skewFrames = (offset * sampleRateHz) / 1_000_000L
        // Nudge PLL / drop-insert sample when absolute skew exceeds 2 ms
        return TimingSyncResult(offset, rtt.coerceAtLeast(0L), skewFrames, kotlin.math.abs(offset) > 2_000L)
    }
}
