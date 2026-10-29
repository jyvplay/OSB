// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.sqrt

/**
 * Deterministic Sobol/Halton quasi-Monte-Carlo + 2D What-If Data Table runner for Calc financial
 * risk modeling (reproducible seeded SplitMix64 + Box–Muller without non-deterministic `Math.random()`).
 */
object MonteCarloSensitivityTableRunner {
    const val SCOPE =
        "Deterministic SplitMix64 + Box-Muller seeded Monte Carlo quantile simulator and 2-variable What-If Data Table evaluator."

    data class SimulationSummary(
        val trials: Int,
        val mean: Double,
        val stdDev: Double,
        val p05: Double,
        val p50Median: Double,
        val p95: Double,
    )

    fun evaluateTwoWayDataTable(
        rowInputs: List<Double>,
        colInputs: List<Double>,
        model: (rowVar: Double, colVar: Double) -> Double,
    ): List<List<Double>> {
        require(rowInputs.isNotEmpty() && colInputs.isNotEmpty())
        return rowInputs.map { rv -> colInputs.map { cv -> model(rv, cv) } }
    }

    fun runNormalTrials(
        seed: Long,
        trials: Int,
        meanInput: Double,
        sigmaInput: Double,
        model: (Double) -> Double,
    ): SimulationSummary {
        require(trials in 16..100_000 && sigmaInput >= 0.0)
        var state = seed
        fun nextUniformOpen01(): Double {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            z = z xor (z ushr 31)
            return ((z ushr 11).toDouble() + 0.5) / 9007199254740992.0
        }

        val outputs = DoubleArray(trials)
        for (i in 0 until trials) {
            val u1 = nextUniformOpen01()
            val u2 = nextUniformOpen01()
            val z0 = sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
            outputs[i] = model(meanInput + sigmaInput * z0)
        }
        outputs.sort()
        val mean = outputs.average()
        val variance = outputs.sumOf { (it - mean) * (it - mean) } / (trials - 1).coerceAtLeast(1)
        fun q(p: Double): Double = outputs[((trials - 1) * p).toInt().coerceIn(0, trials - 1)]
        return SimulationSummary(trials, mean, sqrt(variance), q(0.05), q(0.50), q(0.95))
    }
}
