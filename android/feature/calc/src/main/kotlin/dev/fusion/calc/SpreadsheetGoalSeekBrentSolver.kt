// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.abs

/**
 * Brent–Dekker bracketed inverse-quadratic + secant + bisection **Goal Seek** solver finding
 * input cell `x` such that `f(x) == targetValue` with guaranteed convergence and zero derivative blowup!
 */
object SpreadsheetGoalSeekBrentSolver {
    const val SCOPE =
        "Brent-Dekker superlinear bracketed Goal Seek solver (inverse quadratic + secant + bisection fallback) for Calc spreadsheets."

    data class GoalSeekResult(
        val converged: Boolean,
        val solutionX: Double,
        val achievedValue: Double,
        val residualAbs: Double,
        val iterations: Int,
    )

    fun solve(
        targetValue: Double,
        lowerBound: Double,
        upperBound: Double,
        tolerance: Double = 1e-10,
        maxIterations: Int = 100,
        evaluator: (Double) -> Double,
    ): GoalSeekResult {
        require(lowerBound < upperBound && tolerance > 0.0 && maxIterations in 4..1000)
        var a = lowerBound
        var b = upperBound
        var fa = evaluator(a) - targetValue
        var fb = evaluator(b) - targetValue
        if (abs(fa) <= tolerance) return GoalSeekResult(true, a, fa + targetValue, abs(fa), 0)
        if (abs(fb) <= tolerance) return GoalSeekResult(true, b, fb + targetValue, abs(fb), 0)
        require((fa < 0.0 && fb > 0.0) || (fa > 0.0 && fb < 0.0)) {
            "Goal Seek bracket [$a, $b] does not straddle target $targetValue (fa=$fa, fb=$fb)"
        }

        if (abs(fa) < abs(fb)) { val tx = a; a = b; b = tx; val tf = fa; fa = fb; fb = tf }
        var c = a
        var fc = fa
        var d = b - a
        var mflag = true

        for (iter in 1..maxIterations) {
            if (abs(fb) <= tolerance || abs(b - a) <= tolerance) {
                return GoalSeekResult(true, b, fb + targetValue, abs(fb), iter)
            }
            var s = if (fa != fc && fb != fc) {
                // Inverse quadratic interpolation
                a * fb * fc / ((fa - fb) * (fa - fc)) +
                    b * fa * fc / ((fb - fa) * (fb - fc)) +
                    c * fa * fb / ((fc - fa) * (fc - fb))
            } else {
                // Secant step
                b - fb * (b - a) / (fb - fa)
            }

            val cond1 = s !in minOf((3.0 * a + b) / 4.0, b)..maxOf((3.0 * a + b) / 4.0, b)
            val cond2 = mflag && abs(s - b) >= abs(b - c) / 2.0
            val cond3 = !mflag && abs(s - b) >= abs(c - d) / 2.0
            if (cond1 || cond2 || cond3) {
                s = 0.5 * (a + b)
                mflag = true
            } else {
                mflag = false
            }

            val fs = evaluator(s) - targetValue
            d = c
            c = b; fc = fb
            if (fa * fs < 0.0) { b = s; fb = fs } else { a = s; fa = fs }
            if (abs(fa) < abs(fb)) { val tx = a; a = b; b = tx; val tf = fa; fa = fb; fb = tf }
        }
        return GoalSeekResult(abs(fb) <= tolerance, b, fb + targetValue, abs(fb), maxIterations)
    }
}
