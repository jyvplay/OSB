// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import kotlin.math.abs
import kotlin.math.log10

/**
 * ITU-R BS.1770-4 / EBU R128 K-weighted momentary loudness (`LUFS`) and sample-peak (`dBFS`)
 * meter for **Recall** recorded music & video normalization telemetry.
 */
object LoudnessEbuR128MomentaryMeter {
    const val SCOPE =
        "ITU-R BS.1770-4 48kHz two-stage K-weighting biquad cascade (high-shelf + RLB high-pass) and momentary LUFS / peak dBFS meter."

    data class LoudnessReport(
        val momentaryLufs: Double,
        val samplePeakDbFs: Double,
        val recommendedGainDbToTargetMinus14Lufs: Double,
        val clippingDetected: Boolean,
    )

    fun measureMono48k(samples: FloatArray): LoudnessReport {
        require(samples.isNotEmpty()) { "Audio buffer must be non-empty" }
        // Stage 1 ITU-R BS.1770-4 48 kHz pre-filter high-shelf coefficients
        val b0s = 1.53512485958697; val b1s = -2.69169618940638; val b2s = 1.19839281085285
        val a1s = -1.69065929318241; val a2s = 0.73248077421585
        // Stage 2 RLB high-pass coefficients
        val b0h = 1.0; val b1h = -2.0; val b2h = 1.0
        val a1h = -1.99004745483398; val a2h = 0.99007225036621

        var sx1 = 0.0; var sx2 = 0.0; var sy1 = 0.0; var sy2 = 0.0
        var hx1 = 0.0; var hx2 = 0.0; var hy1 = 0.0; var hy2 = 0.0
        var sumSq = 0.0
        var peak = 0.0
        var clipped = false

        for (raw in samples) {
            val x = raw.toDouble()
            val ax = abs(x)
            if (ax > peak) peak = ax
            if (ax >= 0.9999) clipped = true

            val yShelf = b0s * x + b1s * sx1 + b2s * sx2 - a1s * sy1 - a2s * sy2
            sx2 = sx1; sx1 = x; sy2 = sy1; sy1 = yShelf

            val yK = b0h * yShelf + b1h * hx1 + b2h * hx2 - a1h * hy1 - a2h * hy2
            hx2 = hx1; hx1 = yShelf; hy2 = hy1; hy1 = yK
            sumSq += yK * yK
        }

        val meanSq = sumSq / samples.size.toDouble()
        val lufs = if (meanSq <= 1e-12) -70.0 else -0.691 + 10.0 * log10(meanSq)
        val peakDb = if (peak <= 1e-9) -120.0 else 20.0 * log10(peak)
        val gainDb = (-14.0 - lufs).coerceIn(-24.0, 24.0)
        return LoudnessReport(lufs, peakDb, gainDb, clipped)
    }
}
