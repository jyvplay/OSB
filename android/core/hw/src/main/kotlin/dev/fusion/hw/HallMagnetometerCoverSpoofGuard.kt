// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Vector `#35` — Hall-effect lid sensor + 3-axis magnetometer cover-spoof / induction guard.
 *
 * A lid close is reported by a binary Hall switch. A magnet placed near the sensor can assert "closed"
 * (or "open") without the physical lid moving, which either defeats lock-on-close or fakes a lock-open.
 * The guard cross-validates the hall state against the magnetometer vector's magnitude and slew rate:
 *
 * - expected closed-state magnitude is `>= closeThresholdUT` with a sane slew
 *   (`|d|B|/dt| <= maxSlewUTPerMs`), because a real lid brings the magnet in over tens of milliseconds;
 * - an instantaneous jump (slew far above the mechanical bound) is an injected magnet, not the lid;
 * - a "close-to-open" transition without any magnitude change is a spoofed switch.
 *
 * Fail-safe posture: spoof detection always keeps the device **locked** (never unlocks), because a false
 * "open" is the dangerous direction.
 */
object HallMagnetometerCoverSpoofGuard {
    data class HallSample(val timestampMs: Long, val hallClosed: Boolean, val milliGauss: Triple<Double, Double, Double>)

    enum class Verdict { CONSISTENT, SPOOF_INSTANT_SLEW, SPOOF_STATE_MAGNITUDE_MISMATCH, SPOOF_NO_MAGNET, INSUFFICIENT_DATA }

    data class Assessment(
        val verdict: Verdict,
        val keepLocked: Boolean,
        val magnitudeUT: Double,
        val maxSlewUTPerMs: Double,
        val detail: String,
    )

    fun magnitudeMG(s: HallSample): Double {
        val (x, y, z) = s.milliGauss
        return sqrt(x * x + y * y + z * z) / 10.0 // mG -> µT
    }

    fun assess(
        samples: List<HallSample>,
        closeThresholdUT: Double = 250.0,
        openThresholdUT: Double = 120.0,
        maxSlewUTPerMs: Double = 25.0,
    ): Assessment {
        require(closeThresholdUT > openThresholdUT) { "closeThresholdUT must exceed openThresholdUT" }
        if (samples.size < 2) {
            return Assessment(Verdict.INSUFFICIENT_DATA, true, samples.firstOrNull()?.let { magnitudeMG(it) } ?: 0.0, 0.0,
                "need at least two samples; fail-safe keeps the device locked")
        }
        val sorted = samples.sortedBy { it.timestampMs }
        var maxSlew = 0.0
        for (i in 1 until sorted.size) {
            val dt = (sorted[i].timestampMs - sorted[i - 1].timestampMs).coerceAtLeast(1)
            val slew = abs(magnitudeMG(sorted[i]) - magnitudeMG(sorted[i - 1])) / dt
            if (slew > maxSlew) maxSlew = slew
        }
        val last = sorted.last()
        val mag = magnitudeMG(last)

        if (maxSlew > maxSlewUTPerMs) {
            return Assessment(
                Verdict.SPOOF_INSTANT_SLEW, true, mag, maxSlew,
                "field magnitude changed at ${"%.2f".format(maxSlew)} µT/ms (> mechanical bound ${maxSlewUTPerMs}), injected magnet",
            )
        }
        if (last.hallClosed && mag < closeThresholdUT) {
            return Assessment(
                Verdict.SPOOF_NO_MAGNET, true, mag, maxSlew,
                "hall reports closed but |B|=${"%.1f".format(mag)} µT < ${closeThresholdUT} µT close threshold",
            )
        }
        if (!last.hallClosed && mag >= closeThresholdUT) {
            return Assessment(
                Verdict.SPOOF_STATE_MAGNITUDE_MISMATCH, true, mag, maxSlew,
                "hall reports open while |B|=${"%.1f".format(mag)} µT still matches a closed lid",
            )
        }
        if (!last.hallClosed && mag <= openThresholdUT) {
            return Assessment(Verdict.CONSISTENT, false, mag, maxSlew, "open lid with a quiescent field")
        }
        return Assessment(Verdict.CONSISTENT, last.hallClosed, mag, maxSlew, "lid state consistent with the magnetic field")
    }

    /** Fail-safe lock decision: any spoof forces `true`, and a legitimately closed lid also stays locked. */
    fun shouldStayLocked(assessment: Assessment): Boolean = assessment.keepLocked || assessment.verdict != Verdict.CONSISTENT
}
