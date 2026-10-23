// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * In-app inter-keystroke cadence classifier for the PIN keypad and
 * other sensitive text fields.
 *
 * Thresholds are pinned to published 2026 measurements:
 * - Sustained typing below ~25-30 ms per stroke is biomechanically
 *   implausible; human inter-key jitter has a standard deviation of
 *   roughly 25-70 ms, while microcontroller injection loops show
 *   SD < 1.5 ms (HIDGuard timing analysis, 2026).
 * - Human rates are ~6-18 keys/s; >200 keys/s from one device is
 *   treated as definitively non-human in current defensive guidance
 *   (RRSW offensive-hardware defense notes, 2026).
 *
 * HONEST LIMITS, stated as code: this is an EVADABLE heuristic — QUACK
 * (arXiv 2604.15845, 2026) shows speed/regularity heuristics are
 * defeated by randomized delays, and no in-app timer can see a passive
 * inline hardware keylogger at all (it adds no latency signature an app
 * can measure). Verdicts are therefore MACHINE_LIKE / HUMAN_PLAUSIBLE /
 * INDETERMINATE — never "safe". Pure JVM math over caller-supplied
 * intervals; no key contents are ever passed in, only durations.
 */
object KeyCadence {
    const val MIN_SAMPLES = 8
    const val MACHINE_MEAN_MS = 25.0      // sustained sub-25ms per stroke
    const val MACHINE_SD_MS = 1.5         // injection-loop uniformity
    const val MACHINE_RATE_PER_S = 200.0  // definitively non-human rate
    const val HUMAN_MEAN_LOW_MS = 1000.0 / 18.0   // 18 keys/s upper human bound
    const val HUMAN_MEAN_HIGH_MS = 1000.0 / 6.0   // 6 keys/s lower human bound
    const val HUMAN_SD_LOW_MS = 25.0
    const val HUMAN_SD_HIGH_MS = 70.0

    const val SCOPE = "In-app evadable heuristic over key timings only; cannot observe " +
        "passive inline hardware keyloggers and does not claim OS- or bus-level coverage."

    enum class Verdict { MACHINE_LIKE, HUMAN_PLAUSIBLE, INDETERMINATE }

    data class Report(val meanMs: Double, val sdMs: Double, val ratePerSecond: Double, val verdict: Verdict)

    fun classify(intervalsMs: List<Double>): Report {
        require(intervalsMs.size >= MIN_SAMPLES) { "need at least $MIN_SAMPLES intervals, got ${intervalsMs.size}" }
        for (v in intervalsMs) {
            require(v.isFinite() && v > 0.0) { "interval must be finite and positive: $v" }
        }
        val mean = intervalsMs.sum() / intervalsMs.size
        var varAcc = 0.0
        for (v in intervalsMs) varAcc += (v - mean) * (v - mean)
        val sd = Math.sqrt(varAcc / intervalsMs.size) // population SD, matches Python statistics.pstdev
        val rate = 1000.0 / mean
        val verdict = when {
            rate > MACHINE_RATE_PER_S -> Verdict.MACHINE_LIKE
            mean < MACHINE_MEAN_MS -> Verdict.MACHINE_LIKE
            sd < MACHINE_SD_MS -> Verdict.MACHINE_LIKE
            mean in HUMAN_MEAN_LOW_MS..HUMAN_MEAN_HIGH_MS && sd in HUMAN_SD_LOW_MS..HUMAN_SD_HIGH_MS ->
                Verdict.HUMAN_PLAUSIBLE
            else -> Verdict.INDETERMINATE
        }
        return Report(mean, sd, rate, verdict)
    }
}
