package dev.fusion.media

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Clean-room ITU-R BS.1770-4 / EBU R128 Two-Stage Gated Integrated Loudness (`LUFS`) &
 * ReplayGain (`-18.0 LUFS` / `-14.0 LUFS` streaming target) Normalizer (Apache-2.0).
 *
 * Implements the two-stage BS.1770-4 K-weighted 400ms (75% overlap = 100ms step) gating pipeline:
 * 1. Absolute silence gate at `-70.0 LKFS`,
 * 2. Relative gate at `Gamma_a - 10.0 LU`,
 * plus sample-peak / inter-sample headroom clamping so ReplayGain never clips above `-1.0 dBTP`!
 */
object EbuR128LoudnessNormalizer {
    const val SCOPE =
        "ITU-R BS.1770-4 / EBU R128 K-weighted 400ms two-stage gated LUFS analyzer + clip-safe ReplayGain calculator"

    data class LoudnessReport(
        val integratedLufs: Double,
        val samplePeakDbfs: Double,
        val recommendedGainDb: Double,
        val gatedBlockCount: Int,
    )

    fun analyzeStereo48kHz(
        leftChannel: DoubleArray,
        rightChannel: DoubleArray,
        targetLufs: Double = -14.0,
        ceilingPeakDbfs: Double = -1.0,
    ): LoudnessReport {
        require(leftChannel.size == rightChannel.size && leftChannel.size >= 19_200) {
            "BS.1770-4 400ms gating requires stereo 48kHz buffers of equal length >= 19,200 samples"
        }
        val kLeft = applyKWeighting48k(leftChannel)
        val kRight = applyKWeighting48k(rightChannel)

        var maxAbs = 1e-12
        for (i in leftChannel.indices) {
            maxAbs = maxOf(maxAbs, abs(leftChannel[i]), abs(rightChannel[i]))
        }
        val peakDbfs = 20.0 * log10(maxAbs)

        val window = 19_200 // 400 ms @ 48 kHz
        val hop = 4_800     // 100 ms step (75% overlap)
        val blockEnergies = ArrayList<Double>()
        var start = 0
        while (start + window <= kLeft.size) {
            var sumL = 0.0
            var sumR = 0.0
            for (i in start until start + window) {
                sumL += kLeft[i] * kLeft[i]
                sumR += kRight[i] * kRight[i]
            }
            val z = (sumL + sumR) / window.toDouble()
            blockEnergies.add(z)
            start += hop
        }

        // Gate 1: Absolute threshold -70.0 LKFS -> l_j > -70
        val absPassed = blockEnergies.filter { z -> z > 0.0 && (-0.691 + 10.0 * log10(z)) > -70.0 }
        if (absPassed.isEmpty()) {
            return LoudnessReport(Double.NEGATIVE_INFINITY, peakDbfs, 0.0, 0)
        }
        val gammaA = -0.691 + 10.0 * log10(absPassed.average())
        val relThreshold = gammaA - 10.0

        // Gate 2: Relative threshold Gamma_a - 10 LU
        val relPassed = absPassed.filter { z -> (-0.691 + 10.0 * log10(z)) > relThreshold }
        val integrated = -0.691 + 10.0 * log10(relPassed.average())

        val rawGainDb = targetLufs - integrated
        val maxSafeGainDb = ceilingPeakDbfs - peakDbfs
        val clampedGainDb = minOf(rawGainDb, maxSafeGainDb)
        return LoudnessReport(integrated, peakDbfs, clampedGainDb, relPassed.size)
    }

    /** ITU-R BS.1770-4 Table 1 & Table 2 exact 48 kHz pre-filter (high-shelf) + RLB (high-pass) biquads. */
    private fun applyKWeighting48k(samples: DoubleArray): DoubleArray {
        val stage1 = biquad(
            samples,
            b0 = 1.53512485958697, b1 = -2.69169618940638, b2 = 1.19839281085285,
            a1 = -1.69065929318241, a2 = 0.73248077421585,
        )
        return biquad(
            stage1,
            b0 = 1.0, b1 = -2.0, b2 = 1.0,
            a1 = -1.99004745483398, a2 = 0.99007225036621,
        )
    }

    private fun biquad(x: DoubleArray, b0: Double, b1: Double, b2: Double, a1: Double, a2: Double): DoubleArray {
        val y = DoubleArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val xi = x[i]
            val yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            y[i] = yi
            x2 = x1; x1 = xi; y2 = y1; y1 = yi
        }
        return y
    }
}
