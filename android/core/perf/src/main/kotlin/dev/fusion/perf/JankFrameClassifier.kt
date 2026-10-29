// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

import kotlin.math.ceil

/**
 * Android `FrameMetrics` **Vsync Jank Stage Bottleneck Classifier & Percentile Aggregator**.
 *
 * Classifies frame durations against exact `60 Hz` (`16_666_667 ns`), `90 Hz` (`11_111_111 ns`), or
 * `120 Hz` (`8_333_333 ns`) display refresh budgets and identifies the dominant culprit stage
 * (`INPUT`, `ANIMATION`, `MEASURE_LAYOUT`, `DRAW`, `SYNC_UPLOAD`, `GPU_SWAP`).
 */
object JankFrameClassifier {
    const val SCOPE =
        "60/90/120 Hz FrameMetrics jank classifier, dominant bottleneck stage detector, and p50/p95/p99 frame-time aggregator."

    enum class Stage { INPUT, ANIMATION, MEASURE_LAYOUT, DRAW, SYNC_UPLOAD, GPU_SWAP }

    data class FrameSample(
        val inputNs: Long,
        val animationNs: Long,
        val measureLayoutNs: Long,
        val drawNs: Long,
        val syncUploadNs: Long,
        val gpuSwapNs: Long,
    ) {
        val totalNs: Long
            get() = inputNs + animationNs + measureLayoutNs + drawNs + syncUploadNs + gpuSwapNs

        fun dominantStage(): Stage = listOf(
            Stage.INPUT to inputNs,
            Stage.ANIMATION to animationNs,
            Stage.MEASURE_LAYOUT to measureLayoutNs,
            Stage.DRAW to drawNs,
            Stage.SYNC_UPLOAD to syncUploadNs,
            Stage.GPU_SWAP to gpuSwapNs,
        ).maxBy { it.second }.first
    }

    data class WindowReport(
        val totalFrames: Int,
        val jankyFrames: Int,
        val jankRate: Double,
        val p50Ns: Long,
        val p95Ns: Long,
        val p99Ns: Long,
        val dominantJankStage: Stage?,
    )

    fun budgetNsForRefreshHz(refreshHz: Int): Long {
        require(refreshHz in setOf(60, 90, 120, 144)) { "unsupported refreshHz $refreshHz" }
        return 1_000_000_000L / refreshHz
    }

    fun analyzeWindow(samples: List<FrameSample>, refreshHz: Int = 60): WindowReport {
        require(samples.isNotEmpty()) { "samples must be non-empty" }
        val budget = budgetNsForRefreshHz(refreshHz)
        val totals = samples.map { it.totalNs }.sorted()
        val janky = samples.filter { it.totalNs > budget }
        val topStage = if (janky.isEmpty()) null else {
            janky.groupingBy { it.dominantStage() }.eachCount().maxBy { it.value }.key
        }
        return WindowReport(
            totalFrames = samples.size,
            jankyFrames = janky.size,
            jankRate = janky.size.toDouble() / samples.size,
            p50Ns = percentileNearestRank(totals, 0.50),
            p95Ns = percentileNearestRank(totals, 0.95),
            p99Ns = percentileNearestRank(totals, 0.99),
            dominantJankStage = topStage,
        )
    }

    private fun percentileNearestRank(sorted: List<Long>, p: Double): Long {
        val idx = (ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }
}
