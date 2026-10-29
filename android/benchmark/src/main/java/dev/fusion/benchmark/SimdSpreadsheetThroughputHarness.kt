package dev.fusion.benchmark

/**
 * Clean-room Deterministic Micro-Benchmark Harness & Allocation/Latency Throughput Calculator
 * for Host-Verified Pure-JVM Kernels (Apache-2.0).
 */
object SimdSpreadsheetThroughputHarness {
    const val SCOPE =
        "Deterministic warmup + median/IQR/ops-per-sec pure-JVM micro-benchmark harness"

    data class BenchmarkReport(
        val name: String,
        val measuredRuns: Int,
        val medianMicros: Double,
        val iqrMicros: Double,
        val opsPerSecond: Double,
    )

    fun runMicrobench(
        name: String,
        warmupIterations: Int = 3,
        measuredIterations: Int = 9,
        workload: () -> Any?,
    ): BenchmarkReport {
        require(warmupIterations >= 0 && measuredIterations >= 3)
        var blackholeHash = 0
        repeat(warmupIterations) {
            blackholeHash = blackholeHash xor (workload()?.hashCode() ?: 0)
        }
        val samplesUs = DoubleArray(measuredIterations)
        for (i in 0 until measuredIterations) {
            val t0 = System.nanoTime()
            blackholeHash = blackholeHash xor (workload()?.hashCode() ?: 0)
            val dtUs = ((System.nanoTime() - t0).coerceAtLeast(1L)) / 1_000.0
            samplesUs[i] = dtUs
        }
        if (blackholeHash == Int.MIN_VALUE + 1234567) println(blackholeHash)
        samplesUs.sort()
        val median = samplesUs[measuredIterations / 2]
        val q1 = samplesUs[measuredIterations / 4]
        val q3 = samplesUs[(measuredIterations * 3) / 4]
        val opsSec = 1_000_000.0 / median.coerceAtLeast(0.001)
        return BenchmarkReport(name, measuredIterations, median, q3 - q1, opsSec)
    }
}
