// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Clean-room Normalized Least-Mean-Squares (`NLMS`) Acoustic Echo Canceller + Spectral Noise Gate
 * & Soft-Knee Dynamic Range Limiter (`LN-09`, `LN-10`).
 *
 * Implements the WebRTC-grade adaptive FIR update law:
 *   $$\hat{y}[n] = \mathbf{w}^T \mathbf{x}[n], \quad e[n] = d[n] - \hat{y}[n], \quad
 *     \mathbf{w} \leftarrow \mathbf{w} + \frac{\mu \, e[n] \, \mathbf{x}[n]}{\epsilon + \|\mathbf{x}[n]\|_2^2}$$
 * with double-talk freeze protection when near-end energy dominates far-end reference energy.
 */
object NlmsAcousticEchoGate {
    const val SCOPE =
        "Pure-JVM NLMS adaptive FIR acoustic echo canceller with double-talk freeze guard, RMS noise gate, and soft-knee limiter (LN-09, LN-10)."

    data class EchoCancelResult(
        val cleanedNearEnd: DoubleArray,
        val echoReturnLossEnhancementDb: Double,
        val convergedTapWeights: DoubleArray,
    )

    fun cancelEchoNlms(
        farEndRef: DoubleArray,
        micCapture: DoubleArray,
        filterTaps: Int = 32,
        stepSizeMu: Double = 0.45,
        noiseGateRmsFloor: Double = 0.005,
    ): EchoCancelResult {
        require(farEndRef.isNotEmpty() && farEndRef.size == micCapture.size) { "mismatched audio buffers" }
        require(filterTaps in 4..256 && stepSizeMu in 0.01..1.5) { "invalid NLMS filter parameters" }

        val w = DoubleArray(filterTaps)
        val xRing = DoubleArray(filterTaps)
        val out = DoubleArray(micCapture.size)
        val eps = 1e-6
        var inPow = 0.0
        var outPow = 0.0

        for (n in micCapture.indices) {
            // Shift delay line
            for (k in filterTaps - 1 downTo 1) xRing[k] = xRing[k - 1]
            xRing[0] = farEndRef[n]

            var yHat = 0.0
            var normSq = 0.0
            for (k in 0 until filterTaps) {
                yHat += w[k] * xRing[k]
                normSq += xRing[k] * xRing[k]
            }
            val d = micCapture[n]
            val err = d - yHat
            inPow += d * d
            outPow += err * err

            // Double-talk guard: adapt weights when far-end energy is active and error isn't wild near-end burst
            if (normSq > 1e-5 && abs(err) <= 2.5 * sqrt(normSq / filterTaps + 1e-4)) {
                val gain = stepSizeMu * err / (eps + normSq)
                for (k in 0 until filterTaps) {
                    w[k] += gain * xRing[k]
                }
            }

            // Soft-knee limiter + noise gate
            val gated = if (abs(err) < noiseGateRmsFloor) err * 0.15 else err
            out[n] = (gated / (1.0 + 0.25 * abs(gated))).coerceIn(-1.0, 1.0)
        }

        val erleDb = if (outPow > 1e-12 && inPow > 1e-12) {
            10.0 * kotlin.math.log10(inPow / outPow)
        } else {
            0.0
        }
        return EchoCancelResult(out, erleDb, w)
    }
}
