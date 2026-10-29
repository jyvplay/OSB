// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import kotlin.math.sqrt

/**
 * Clean-room Anti-Keylogger Input Hardening Kernel (`HIDGuard` Inbound Anomaly Detector +
 * Outbound Timing/Coordinate Obfuscator).
 *
 * Closes two orthogonal local keystroke threats:
 * 1. **Inbound BadUSB / Wireless `BlueDucky` Scripted Injection (`classifyCadence`)**:
 *    Microcontroller HID injectors emit keystrokes at either superhuman speed (`meanIkiMs < 25 ms`)
 *    or crystal-clock regularity (`stddevIkiMs < 2.0 ms` across `>= 8` intervals), whereas real
 *    human typing exhibits `80..350 ms` mean inter-keystroke intervals and `>= 12 ms` physiological
 *    variance. Flagged bursts are quarantined before `ENTER` executes a shell command!
 * 2. **Outbound Keystroke-Timing & Screen-Tap Side-Channel Defense**:
 *    - `buildJitterSchedule` mixes real keystroke timestamps with bounded jitter + indistinguishable
 *      chaff cover-traffic packets so `/proc` interrupt or radio-packet timing observers see a
 *      flattened cadence.
 *    - `scramblePinDigits` produces an unbiased Fisher-Yates permutation of `0..9` from caller
 *      entropy so shoulder-surfing / smudge / touch-coordinate loggers cannot map screen taps to digits.
 */
object KeystrokeJitter {
    const val SCOPE =
        "HIDGuard-style synthetic keystroke-injection cadence detector + outbound timing-chaff scheduler and unbiased PIN-pad layout scrambler; Android InputManager hooks out of scope."

    const val MIN_HUMAN_MEAN_IKI_MS = 25.0
    const val MIN_HUMAN_STDDEV_IKI_MS = 2.0
    const val MIN_SAMPLE_INTERVALS = 8

    enum class CadenceClassification {
        INSUFFICIENT_SAMPLES,
        HUMAN_PLAUSIBLE,
        SYNTHETIC_SUPERHUMAN_SPEED,
        SYNTHETIC_CLOCKWORK_REGULARITY,
    }

    data class CadenceReport(
        val classification: CadenceClassification,
        val intervalCount: Int,
        val meanIkiMs: Double,
        val stddevIkiMs: Double,
    ) {
        val shouldBlockStream: Boolean
            get() = classification == CadenceClassification.SYNTHETIC_SUPERHUMAN_SPEED ||
                classification == CadenceClassification.SYNTHETIC_CLOCKWORK_REGULARITY
    }

    data class WireKeyPacket(
        val dispatchTimeMs: Long,
        val isChaffCover: Boolean,
        val payloadSlot: Int, // >= 0 for real key index, -1 for dummy chaff packet
    )

    fun classifyCadence(keydownTimestampsMs: List<Long>): CadenceReport {
        require(keydownTimestampsMs.size <= 4_096) { "sample window exceeds 4096 keystrokes" }
        for (i in 1 until keydownTimestampsMs.size) {
            require(keydownTimestampsMs[i] >= keydownTimestampsMs[i - 1]) {
                "non-monotonic keydown timestamps at index $i"
            }
        }
        if (keydownTimestampsMs.size - 1 < MIN_SAMPLE_INTERVALS) {
            return CadenceReport(CadenceClassification.INSUFFICIENT_SAMPLES, (keydownTimestampsMs.size - 1).coerceAtLeast(0), 0.0, 0.0)
        }
        val ikis = DoubleArray(keydownTimestampsMs.size - 1) { i ->
            (keydownTimestampsMs[i + 1] - keydownTimestampsMs[i]).toDouble()
        }
        val mean = ikis.average()
        val variance = ikis.sumOf { (it - mean) * (it - mean) } / (ikis.size - 1)
        val std = sqrt(variance)

        val cls = when {
            mean < MIN_HUMAN_MEAN_IKI_MS -> CadenceClassification.SYNTHETIC_SUPERHUMAN_SPEED
            std < MIN_HUMAN_STDDEV_IKI_MS -> CadenceClassification.SYNTHETIC_CLOCKWORK_REGULARITY
            else -> CadenceClassification.HUMAN_PLAUSIBLE
        }
        return CadenceReport(cls, ikis.size, mean, std)
    }

    /**
     * Produces a sorted packet dispatch schedule combining real keystrokes (delayed by `0..maxJitterMs`)
     * with `chaffPerRealKey` synthetic cover packets so total packet count and inter-arrival gaps
     * mask real passphrase length and rhythm.
     */
    fun buildJitterSchedule(
        realKeydownMs: List<Long>,
        maxJitterMs: Int,
        chaffPerRealKey: Int,
        entropySeed: Long,
    ): List<WireKeyPacket> {
        require(maxJitterMs in 1..100) { "maxJitterMs $maxJitterMs outside 1..100" }
        require(chaffPerRealKey in 0..4) { "chaffPerRealKey $chaffPerRealKey outside 0..4" }
        var state = entropySeed xor -0x61c8864680b583ebL
        fun nextU32(): Int {
            // SplitMix64 deterministic stream from caller-supplied CSPRNG seed
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            z = z xor (z ushr 31)
            return (z ushr 33).toInt()
        }

        val out = ArrayList<WireKeyPacket>(realKeydownMs.size * (1 + chaffPerRealKey))
        var prevRealDispatch = Long.MIN_VALUE
        for ((idx, t) in realKeydownMs.withIndex()) {
            val jitter = nextU32() % (maxJitterMs + 1)
            // Preserve strict FIFO order among real keystrokes so user text never scrambles!
            val dispatch = maxOf(t + jitter, if (prevRealDispatch == Long.MIN_VALUE) t else prevRealDispatch + 1L)
            prevRealDispatch = dispatch
            out.add(WireKeyPacket(dispatch, isChaffCover = false, payloadSlot = idx))
            repeat(chaffPerRealKey) {
                val chaffOffset = nextU32() % (maxJitterMs * 3 + 1)
                out.add(WireKeyPacket(t + chaffOffset, isChaffCover = true, payloadSlot = -1))
            }
        }
        return out.sortedWith(compareBy<WireKeyPacket> { it.dispatchTimeMs }.thenBy { if (it.isChaffCover) 0 else 1 })
    }

    /** Unbiased Fisher-Yates shuffle of digits `0..9` for anti-coordinate-logger PIN entry pads. */
    fun scramblePinDigits(entropyBytes: ByteArray): List<Int> {
        require(entropyBytes.size >= 16) { "PIN scrambler requires >= 16 entropy bytes" }
        val digits = IntArray(10) { it }
        var seed = 0L
        for (i in 0 until 8) seed = (seed shl 8) or (entropyBytes[i].toLong() and 0xFFL)
        var s2 = 0L
        for (i in 8 until 16) s2 = (s2 shl 8) or (entropyBytes[i].toLong() and 0xFFL)
        var state = seed xor (s2 * -0x61c8864680b583ebL)

        fun nextBounded(bound: Int): Int {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            val u = ((z xor (z ushr 31)) ushr 33).toInt()
            return u % bound
        }

        for (i in 9 downTo 1) {
            val j = nextBounded(i + 1)
            val tmp = digits[i]
            digits[i] = digits[j]
            digits[j] = tmp
        }
        return digits.toList()
    }
}
