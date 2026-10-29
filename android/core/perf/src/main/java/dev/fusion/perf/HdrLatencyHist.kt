// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Clean-room logarithmic sub-bucket High-Dynamic-Range latency histogram (`HdrLatencyHist`).
 *
 * Powers `:core:perf` frame-time & document-parse latency telemetry without unbounded sample arrays:
 * - Covers `0 .. maxTrackableValue` in $O(1)$ space/time using power-of-two coarse buckets subdivided into
 *   `subBucketCount = 2^unitMagnitudeStep` linear sub-buckets (Gil Tene HdrHistogram principle, written clean-room).
 * - Supports Gil Tene's **Coordinated Omission** correction (`recordCorrectedValue(valueNs, expectedIntervalNs)`),
 *   backfilling synthetic stalled-interval samples when a single GC/IO pause blocks periodic probes!
 * - Deterministic `valueAtPercentile(p)` (`p50`, `p95`, `p99`, `p99.9`), `mean()`, and `mergeFrom(other)`.
 */
class HdrLatencyHist(
    val highestTrackableValue: Long = 3_600_000_000_000L, // 1 hour in ns
    val subBucketBits: Int = 8, // 256 sub-buckets -> ~0.39% relative precision
) {
    companion object {
        const val SCOPE =
            "O(1) logarithmic sub-bucket HDR latency histogram with Coordinated Omission correction, exact min/max, p50..p99.99, and additive merge; concurrent lock-free writer ring out of scope."
    }

    init {
        require(subBucketBits in 4..12) { "subBucketBits must be in 4..12: $subBucketBits" }
        require(highestTrackableValue >= (1L shl subBucketBits)) { "highestTrackableValue too small" }
    }

    private val subBucketCount = 1 shl subBucketBits
    private val subBucketHalfCount = subBucketCount ushr 1
    private val subBucketMask = subBucketCount - 1
    private val bucketCount: Int
    private val counts: LongArray

    var totalCount: Long = 0L
        private set
    var minNonZero: Long = Long.MAX_VALUE
        private set
    var maxValue: Long = 0L
        private set
    private var weightedSum: Double = 0.0

    init {
        var largest = subBucketCount.toLong()
        var bucketsNeeded = 1
        while (largest <= highestTrackableValue) {
            largest = largest shl 1
            bucketsNeeded++
        }
        bucketCount = bucketsNeeded
        counts = LongArray((bucketCount + 1) * subBucketHalfCount)
    }

    fun recordValue(value: Long, count: Long = 1L) {
        require(value in 0..highestTrackableValue) {
            "Value $value outside trackable range 0..$highestTrackableValue"
        }
        require(count >= 1L) { "count must be >= 1" }
        val idx = countsIndexFor(value)
        counts[idx] += count
        totalCount += count
        if (value > 0 && value < minNonZero) minNonZero = value
        if (value > maxValue) maxValue = value
        weightedSum += value.toDouble() * count.toDouble()
    }

    fun recordCorrectedValue(value: Long, expectedInterval: Long) {
        recordValue(value)
        if (expectedInterval > 0L && value > expectedInterval) {
            var missing = value - expectedInterval
            while (missing >= expectedInterval) {
                recordValue(missing)
                missing -= expectedInterval
            }
        }
    }

    fun valueAtPercentile(percentile: Double): Long {
        require(percentile in 0.0..100.0) { "percentile must be in 0..100: $percentile" }
        if (totalCount == 0L) return 0L
        val targetRank = max(1L, ceil((percentile / 100.0) * totalCount).toLong())
        var running = 0L
        for (idx in counts.indices) {
            val c = counts[idx]
            if (c > 0L) {
                running += c
                if (running >= targetRank) {
                    return min(maxValue, highestEquivalentValue(valueFromIndex(idx)))
                }
            }
        }
        return maxValue
    }

    fun mean(): Double = if (totalCount == 0L) 0.0 else weightedSum / totalCount.toDouble()

    fun mergeFrom(other: HdrLatencyHist) {
        require(other.subBucketBits == subBucketBits && other.counts.size == counts.size) {
            "Incompatible HdrLatencyHist geometry"
        }
        for (i in counts.indices) counts[i] += other.counts[i]
        totalCount += other.totalCount
        if (other.minNonZero < minNonZero) minNonZero = other.minNonZero
        if (other.maxValue > maxValue) maxValue = other.maxValue
        weightedSum += other.weightedSum
    }

    private fun countsIndexFor(value: Long): Int {
        if (value < subBucketCount) return value.toInt()
        val msb = 63 - java.lang.Long.numberOfLeadingZeros(value)
        val bucketIdx = msb - subBucketBits + 1
        val subIdx = ((value ushr bucketIdx) and subBucketMask.toLong()).toInt()
        return (bucketIdx + 1) * subBucketHalfCount + (subIdx - subBucketHalfCount)
    }

    private fun valueFromIndex(index: Int): Long {
        if (index < subBucketCount) return index.toLong()
        val bucketIdx = (index / subBucketHalfCount) - 1
        val subIdx = (index % subBucketHalfCount) + subBucketHalfCount
        return subIdx.toLong() shl bucketIdx
    }

    private fun highestEquivalentValue(v: Long): Long {
        if (v < subBucketCount) return v
        val msb = 63 - java.lang.Long.numberOfLeadingZeros(v)
        val shift = msb - subBucketBits + 1
        val step = 1L shl shift
        return ((v ushr shift) shl shift) + step - 1L
    }
}
