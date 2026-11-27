// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * R146 — era-aware NTP timestamp codec for RAOP/AirPlay sync packets.
 *
 * NTP timestamps are a 32-bit seconds field counting from 1900-01-01 plus a
 * 32-bit fraction of a second. The seconds field wraps every ~136 years, so a
 * bare conversion to epoch time is ambiguous; this codec resolves the era
 * against a caller-supplied reference epoch (typically "now") by choosing the
 * unique representative within ±68 years of the reference, then converts
 * exactly with integer arithmetic. The reverse direction always emits the
 * era that lands within [MIN_EPOCH_MS, MAX_EPOCH_MS].
 *
 * Declared narrowings: epoch milliseconds in [0, 2100-01-01]; fraction uses
 * floor division (never rounds up into the next second); the forward lane
 * refuses a timestamp whose resolved era would fall outside the supported
 * epoch window; the reference must be inside the window too.
 */
object NtpTimestampCodec {
    const val SCOPE =
        "Era-resolved NTP 32.32 fixed-point <-> epoch-ms conversion with integer-exact floor rounding; no sockets, no clocks read."

    /** 1900-01-01 00:00:00 UTC in epoch milliseconds. */
    const val NTP_EPOCH_OFFSET_MS = 2_208_988_800_000L

    const val MIN_EPOCH_MS = 0L
    const val MAX_EPOCH_MS = 4_102_444_800_000L // 2100-01-01 00:00:00 UTC

    private const val UINT32 = 1L shl 32
    /** One NTP era is 2^32 seconds. */
    private const val ERA_MS = UINT32 * 1000L

    private fun checkEpoch(ms: Long, what: String): Long {
        require(ms in MIN_EPOCH_MS..MAX_EPOCH_MS) { "$what out of supported epoch range" }
        return ms
    }

    /**
     * Forward lane: `fwd;referenceMs;seconds;fraction` → epoch milliseconds.
     * The reference selects the era: result = seconds*1000 + fraction*1000/2^32
     * + NTP_EPOCH_OFFSET_MS - k*ERA_MS for the unique integer k placing the
     * result within ±ERA/2 of the reference.
     */
    fun toEpoch(referenceMs: Long, ntpSeconds: Long, ntpFraction: Long): Long {
        checkEpoch(referenceMs, "referenceMs")
        require(ntpSeconds in 0 until UINT32) { "ntpSeconds must be a uint32" }
        require(ntpFraction in 0 until UINT32) { "ntpFraction must be a uint32" }
        val baseMs = try {
            Math.addExact(Math.multiplyExact(ntpSeconds, 1000L), NTP_EPOCH_OFFSET_MS)
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("NTP seconds overflow epoch arithmetic", e)
        }
        val fracMs = ntpFraction * 1000L / UINT32 // floor, integer-exact
        val naive = try {
            Math.addExact(baseMs, fracMs)
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("NTP timestamp overflows epoch arithmetic", e)
        }
        // Choose the era nearest the reference: k = round(diff / ERA) with
        // ties resolving toward the later era (floor(x + 1/2)).
        val diff = naive - referenceMs
        val k = Math.floorDiv(diff + ERA_MS / 2, ERA_MS)
        val eraAdjusted = naive - k * ERA_MS
        require(eraAdjusted in MIN_EPOCH_MS..MAX_EPOCH_MS) {
            "resolved NTP timestamp outside the supported epoch range"
        }
        return eraAdjusted
    }

    /**
     * Reverse lane: `rev;epochMs` → `seconds;fraction`. The seconds field is
     * emitted modulo 2^32 (the wire format), and the fraction is the floor of
     * the millisecond remainder scaled to 2^32.
     */
    fun fromEpoch(epochMs: Long): Pair<Long, Long> {
        checkEpoch(epochMs, "epochMs")
        val withOffset = epochMs + NTP_EPOCH_OFFSET_MS
        require(withOffset >= 0) { "epoch before the NTP era" }
        val seconds = withOffset / 1000L
        val millis = withOffset % 1000L
        val wireSeconds = seconds % UINT32
        val fraction = millis * UINT32 / 1000L
        require(fraction in 0 until UINT32) { "fraction out of uint32 range" }
        return wireSeconds to fraction
    }

    fun renderPair(p: Pair<Long, Long>): String = "${p.first};${p.second}"
}
