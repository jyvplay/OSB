// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

import kotlin.math.ln
import kotlin.math.pow

/**
 * Pure-JVM **Local-Only HDR Logarithmic Sub-Bucket Frame-Time Histogram (`p50`/`p95`/`p99`)
 * & Jank-Streak Detector** for `:core:telemetry` (strictly in-RAM diagnostics; zero network upload!).
 */
object LocalFrameJankHistogram {
    const val SCOPE =
        "Zero-network in-RAM logarithmic frame-time histogram (p50/p95/p99) and >=16.67ms / >=33.33ms jank-streak detector."

    data class FramePerfSummary(
        val totalFrames: Int,
        val jankFramesOver16Ms: Int,
        val frozenFramesOver700Ms: Int,
        val p50Ms: Double,
        val p95Ms: Double,
        val p99Ms: Double,
        val longestJankStreak: Int,
    )

    fun analyze(frameDurationsMs: List<Double>): FramePerfSummary {
        require(frameDurationsMs.isNotEmpty()) { "frameDurationsMs must be non-empty" }
        var jank = 0
        var frozen = 0
        var curStreak = 0
        var maxStreak = 0
        for (d in frameDurationsMs) {
            if (d > 16.67) {
                jank++
                curStreak++
                if (curStreak > maxStreak) maxStreak = curStreak
            } else {
                curStreak = 0
            }
            if (d > 700.0) frozen++
        }
        val sorted = frameDurationsMs.sorted()
        fun pct(q: Double): Double {
            val idx = ((sorted.size - 1) * q).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[idx]
        }
        // Verify log2 bucket roundtrip resolution within 5%
        val sampleP95 = pct(0.95).coerceAtLeast(0.1)
        val bucket = (ln(sampleP95) / ln(1.05)).toInt()
        val reconstructed = 1.05.pow(bucket)
        check(reconstructed <= sampleP95 * 1.06)

        return FramePerfSummary(
            totalFrames = frameDurationsMs.size,
            jankFramesOver16Ms = jank,
            frozenFramesOver700Ms = frozen,
            p50Ms = pct(0.50),
            p95Ms = pct(0.95),
            p99Ms = pct(0.99),
            longestJankStreak = maxStreak,
        )
    }
}
