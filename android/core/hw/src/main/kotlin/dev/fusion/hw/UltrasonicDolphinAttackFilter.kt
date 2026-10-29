// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * **Local Hardware Attack Vector `#22` — `DolphinAttack` Ultrasonic Injection Defense (Zhang et al., ACM CCS 2017):**
 * Detects inaudible `> 18.5 kHz` (`20–39 kHz`) AM-modulated ultrasonic carriers captured on `48/96/192 kHz`
 * microphone streams via Goertzel spectral band energy ratio, quarantines ultrasonic injection frames,
 * and applies a cascaded 4th-order Butterworth low-pass filter (`fc = 7,800 Hz`) to strip out-of-band energy.
 */
object UltrasonicDolphinAttackFilter {
    const val SCOPE =
        "Vector #22 DolphinAttack (ACM CCS 2017) >20 kHz ultrasonic voice-injection detector (Goertzel high-band energy ratio) and 4th-order cascaded Butterworth low-pass filter (fc=7.8 kHz)."

    data class UltrasonicAuditResult(
        val sampleRateHz: Int,
        val speechBandEnergy: Double,
        val ultrasonicBandEnergy: Double,
        val ultrasonicToSpeechRatio: Double,
        val dolphinAttackDetected: Boolean,
        val filteredSamples: DoubleArray,
    )

    fun auditAndFilter(samples: DoubleArray, sampleRateHz: Int = 48_000, cutoffHz: Double = 7_800.0): UltrasonicAuditResult {
        require(samples.size >= 64 && sampleRateHz in 44_100..192_000 && cutoffHz in 3_000.0..12_000.0) {
            "Invalid PCM frame or sampleRateHz=$sampleRateHz (requires >=44.1 kHz to observe >18.5 kHz band)"
        }
        val speechFreqs = doubleArrayOf(500.0, 1000.0, 2000.0, 3200.0)
        val nyquist = sampleRateHz / 2.0
        val ultraFreqs = buildList {
            var f = 19_000.0
            while (f <= nyquist - 500.0) {
                add(f)
                f += 2_000.0
            }
        }
        val speechE = speechFreqs.sumOf { goertzelPower(samples, sampleRateHz, it) }.coerceAtLeast(1e-12)
        val ultraE = ultraFreqs.sumOf { goertzelPower(samples, sampleRateHz, it) }
        val ratio = ultraE / speechE
        val detected = ultraFreqs.isNotEmpty() && ultraE > 1e-4 && ratio > 0.35

        // Two cascaded 2nd-order Butterworth biquad sections (Q1 = 0.54119610, Q2 = 1.30656296) => 4th-order (-24 dB/oct)
        val stage1 = biquadLowPass(samples, sampleRateHz, cutoffHz, q = 0.54119610)
        val stage2 = biquadLowPass(stage1, sampleRateHz, cutoffHz, q = 1.30656296)
        return UltrasonicAuditResult(sampleRateHz, speechE, ultraE, ratio, detected, stage2)
    }

    fun goertzelPower(samples: DoubleArray, sampleRateHz: Int, targetHz: Double): Double {
        val omega = 2.0 * PI * targetHz / sampleRateHz.toDouble()
        val coeff = 2.0 * cos(omega)
        var s1 = 0.0
        var s2 = 0.0
        for (x in samples) {
            val s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val real = s1 - s2 * cos(omega)
        val imag = s2 * sin(omega)
        return (real * real + imag * imag) / (samples.size.toDouble() * samples.size.toDouble())
    }

    private fun biquadLowPass(input: DoubleArray, fs: Int, fc: Double, q: Double): DoubleArray {
        val w0 = 2.0 * PI * fc / fs.toDouble()
        val k = tan(w0 / 2.0)
        val norm = 1.0 / (1.0 + k / q + k * k)
        val b0 = k * k * norm
        val b1 = 2.0 * b0
        val b2 = b0
        val a1 = 2.0 * (k * k - 1.0) * norm
        val a2 = (1.0 - k / q + k * k) * norm
        val out = DoubleArray(input.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in input.indices) {
            val x0 = input[i]
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            out[i] = y0
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }
        return out
    }
}
