// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Lock-free bounded power-of-two ring buffer (`AtomicLongArray` + monotonic sequence numbers)
 * capturing high-rate frame/network/Recall telemetry without heap allocation on hot paths.
 */
object RingBufferLockFreeTelemetry {
    const val SCOPE =
        "Allocation-free power-of-two AtomicLongArray telemetry ring buffer with exact p50/p95/p99 latency quantile snapshot."

    class LongRing(capacityPowerOfTwo: Int = 256) {
        private val cap: Int = Integer.highestOneBit(capacityPowerOfTwo.coerceIn(8, 65536))
        private val mask: Int = cap - 1
        private val slots = AtomicLongArray(cap)
        private val writeSeq = AtomicLong(0L)

        fun recordMicros(durationMicros: Long) {
            val seq = writeSeq.getAndIncrement()
            slots.set((seq.toInt()) and mask, durationMicros.coerceAtLeast(0L))
        }

        fun snapshotQuantiles(): Triple<Long, Long, Long> {
            val written = writeSeq.get()
            if (written == 0L) return Triple(0L, 0L, 0L)
            val count = minOf(written, cap.toLong()).toInt()
            val sample = LongArray(count)
            val start = written - count
            for (i in 0 until count) {
                sample[i] = slots.get(((start + i).toInt()) and mask)
            }
            sample.sort()
            fun pct(p: Double): Long = sample[((count - 1) * p).toInt().coerceIn(0, count - 1)]
            return Triple(pct(0.50), pct(0.95), pct(0.99))
        }

        fun totalEvents(): Long = writeSeq.get()
    }
}
