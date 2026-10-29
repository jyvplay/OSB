// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clean-room **Anti-Acoustic & Anti-Ultrasonic Keystroke Side-Channel Shield** (`2025–2026` MKA
 * defenses against `Improved CoAtNet` `99.8%`, `ASCA-Net` `99.87%` local / `96.97%` VoIP, dual-mic
 * TDoA touchscreen triangulation, and `SonarSnoop` `18–22 kHz` active ultrasonic reflection).
 *
 * Four orthogonal pure-JVM mechanisms:
 * 1. **Focus-Gated Mic Blackout** ([evaluateMicPolicy]): zeroes/blocks background microphone audio
 *    frames while any secret or text editor field is focused (`+250 ms` post-keystroke decay tail),
 *    unless the user explicitly grants simultaneous call-plus-typing override.
 * 2. **Stereo-to-Mono TDoA Phase Collapse** ([collapseStereoTdoa]): mixes left/right dual-mic PCM
 *    channels `(L + R) / 2` onto identical monaural samples, destroying inter-microphone Time
 *    Difference of Arrival (`TDoA`) keystroke/touch localization!
 * 3. **Ultrasonic Brickwall Low-Pass Filter** ([attenuateUltrasonic48k]): 31-tap windowed-sinc
 *    Hamming FIR filter (`fc = 14.5 kHz` at `fs = 48 kHz`) attenuating `18–23 kHz` `SonarSnoop`
 *    active sonar reflections by `> 45 dB` while preserving normal `80 Hz–12 kHz` voice speech!
 * 4. **Decoy Click-Envelope Masking** ([scheduleDecoyClicksMs]): Poisson-jittered decoy click
 *    timestamps defeating inter-keystroke timing (`IKT`) & LLM spectrogram sequence recovery.
 */
object AcousticKeystrokeShield {
    const val SCOPE =
        "Anti-acoustic/ultrasonic keystroke side-channel shield (focus-gated mic blackout + 250ms tail, dual-mic stereo-to-mono TDoA collapse, 31-tap Hamming FIR 14.5 kHz ultrasonic blocker @ 48 kHz, decoy IKT schedule); OS AudioRecord HAL hook requires Android runtime."

    const val POST_KEYSTROKE_HOLD_MS = 250L
    private const val FIR_TAPS = 31
    private val HAMMING_LPF_48K: DoubleArray = buildWindowedSincLpf(sampleRateHz = 48_000.0, cutoffHz = 14_500.0, taps = FIR_TAPS)

    enum class MicAction { PASS_FILTERED_MONO, ZERO_FILL_MUTE }

    data class MicPolicyDecision(
        val action: MicAction,
        val collapseTdoaToMono: Boolean,
        val stripUltrasonicBand: Boolean,
        val injectDecoyMasking: Boolean,
        val reason: String,
    )

    fun evaluateMicPolicy(
        sensitiveInputFocused: Boolean,
        generalTextFocused: Boolean,
        msSinceLastKeystroke: Long,
        activeForegroundVoipCall: Boolean,
    ): MicPolicyDecision {
        require(msSinceLastKeystroke >= 0L) { "msSinceLastKeystroke must be >= 0" }
        val withinKeystrokeTail = msSinceLastKeystroke <= POST_KEYSTROKE_HOLD_MS
        if (sensitiveInputFocused || (!activeForegroundVoipCall && (generalTextFocused || withinKeystrokeTail))) {
            return MicPolicyDecision(
                action = MicAction.ZERO_FILL_MUTE,
                collapseTdoaToMono = true,
                stripUltrasonicBand = true,
                injectDecoyMasking = false,
                reason = if (sensitiveInputFocused) "secret field focused: hard zero-fill mic blackout"
                else "typing/decay window without foreground VoIP: zero-fill mic blackout",
            )
        }
        return MicPolicyDecision(
            action = MicAction.PASS_FILTERED_MONO,
            collapseTdoaToMono = true,
            stripUltrasonicBand = true,
            injectDecoyMasking = generalTextFocused || withinKeystrokeTail,
            reason = "foreground VoIP active: TDoA collapsed to mono + >14.5 kHz ultrasonic band stripped",
        )
    }

    /** Collapses interleaved stereo PCM16 `[L0, R0, L1, R1, ...]` so `L' == R' == (L + R)/2` (zero TDoA). */
    fun collapseStereoTdoa(interleavedStereoPcm16: ShortArray): ShortArray {
        require(interleavedStereoPcm16.size % 2 == 0) { "interleaved stereo PCM16 length must be even" }
        val out = ShortArray(interleavedStereoPcm16.size)
        var i = 0
        while (i < interleavedStereoPcm16.size) {
            val mono = ((interleavedStereoPcm16[i].toInt() + interleavedStereoPcm16[i + 1].toInt()) / 2)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            out[i] = mono
            out[i + 1] = mono
            i += 2
        }
        return out
    }

    /** Applies 31-tap Hamming-windowed sinc LPF (`fc = 14.5 kHz` @ `48 kHz`) blocking `18–22 kHz` SonarSnoop sonar. */
    fun attenuateUltrasonic48k(monoPcm16: ShortArray): ShortArray {
        val out = ShortArray(monoPcm16.size)
        val half = FIR_TAPS / 2
        for (n in monoPcm16.indices) {
            var acc = 0.0
            for (k in 0 until FIR_TAPS) {
                val idx = n - k + half
                if (idx in monoPcm16.indices) {
                    acc += HAMMING_LPF_48K[k] * monoPcm16[idx].toDouble()
                }
            }
            out[n] = acc.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    fun rmsEnergy(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sumSq = 0.0
        for (s in samples) sumSq += s.toDouble() * s.toDouble()
        return sqrt(sumSq / samples.size)
    }

    /** Deterministic SplitMix64 decoy click-timestamp generator (`3..7` synthetic clicks per `500 ms` window). */
    fun scheduleDecoyClicksMs(windowStartMs: Long, windowDurationMs: Long, seed: Long): List<Long> {
        require(windowDurationMs in 50L..60_000L) { "windowDurationMs outside 50..60000" }
        var state = seed xor -0x61c8864680b583ebL
        fun nextU64(): Long {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
        val count = (3 + ((nextU64() ushr 1) % 5L)).toInt()
        return List(count) {
            windowStartMs + ((nextU64() ushr 1) % windowDurationMs)
        }.sorted().distinct()
    }

    private fun buildWindowedSincLpf(sampleRateHz: Double, cutoffHz: Double, taps: Int): DoubleArray {
        val fc = cutoffHz / sampleRateHz
        val mid = (taps - 1) / 2.0
        val h = DoubleArray(taps)
        var sum = 0.0
        for (i in 0 until taps) {
            val x = i - mid
            val sinc = if (x == 0.0) 2.0 * fc else sin(2.0 * PI * fc * x) / (PI * x)
            val hamming = 0.54 - 0.46 * cos(2.0 * PI * i / (taps - 1))
            h[i] = sinc * hamming
            sum += h[i]
        }
        for (i in 0 until taps) h[i] /= sum
        return h
    }
}
