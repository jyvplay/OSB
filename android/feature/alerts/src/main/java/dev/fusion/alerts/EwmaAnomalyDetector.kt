// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Clean-room Exponentially Weighted Moving Average (`EWMA`) & variance $z$-score spike detector (`EwmaAnomalyDetector`).
 */
class EwmaAnomalyDetector(
    val alpha: Double = 0.2,
    val zThreshold: Double = 3.0,
    val warmupSamples: Int = 5,
) {
    companion object {
        const val SCOPE =
            "Streaming O(1)-memory EWMA mean + variance z-score anomaly detector (warmup guard, spike verdict); seasonal Holt-Winters decomposition out of scope."
    }

    init {
        require(alpha in 0.01..0.99 && zThreshold > 0.5 && warmupSamples >= 2) { "Invalid EWMA parameters" }
    }

    data class ObservationResult(val mean: Double, val stdDev: Double, val zScore: Double, val isAnomaly: Boolean)

    private var count = 0
    private var ewmaMean = 0.0
    private var ewmaVar = 0.0

    fun observe(sample: Double): ObservationResult {
        require(sample.isFinite()) { "Non-finite metric sample: $sample" }
        count++
        if (count == 1) {
            ewmaMean = sample
            ewmaVar = 0.0
            return ObservationResult(ewmaMean, 0.0, 0.0, false)
        }
        val diff = sample - ewmaMean
        val stdBefore = sqrt(ewmaVar.coerceAtLeast(1e-12))
        val z = abs(diff) / stdBefore
        val anomaly = count > warmupSamples && z > zThreshold
        // Update baseline cleanly (dampen update on extreme spike so a single attack spike doesn't poison baseline!)
        val effAlpha = if (anomaly) alpha * 0.1 else alpha
        val incr = effAlpha * diff
        ewmaMean += incr
        ewmaVar = (1.0 - effAlpha) * (ewmaVar + diff * incr)
        return ObservationResult(ewmaMean, sqrt(ewmaVar.coerceAtLeast(0.0)), z, anomaly)
    }
}
