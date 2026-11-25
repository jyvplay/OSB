// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

/**
 * Bounded per-iteration allocation delta and budget checker.
 */
class BenchmarkAllocationDelta private constructor() {
    companion object {
        fun bytesPerOp(beforeBytes: Long, afterBytes: Long, iterations: Int): Double {
            require(beforeBytes >= 0L && afterBytes >= beforeBytes) { "invalid byte counts" }
            require(iterations > 0) { "iterations must be positive" }
            return (afterBytes - beforeBytes).toDouble() / iterations.toDouble()
        }

        fun withinBudget(beforeBytes: Long, afterBytes: Long, iterations: Int, maxBytesPerOp: Double): Boolean {
            require(maxBytesPerOp >= 0.0 && maxBytesPerOp.isFinite()) { "invalid budget" }
            return bytesPerOp(beforeBytes, afterBytes, iterations) <= maxBytesPerOp
        }
    }
}
