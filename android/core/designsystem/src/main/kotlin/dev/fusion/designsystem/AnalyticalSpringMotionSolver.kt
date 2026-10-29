// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-JVM **Analytical Critically-Damped & Under-Damped Harmonic Spring Curve Solver**
 * for `:core:ui`, computing closed-form position `x(t)` and velocity `v(t)` without numerical
 * Euler drift across variable `60/90/120 Hz` refresh rates.
 */
object AnalyticalSpringMotionSolver {
    const val SCOPE =
        "Closed-form analytical harmonic spring evaluator (critically damped & underdamped) for refresh-rate-independent UI transitions."

    data class SpringState(val position: Double, val velocity: Double, val atRest: Boolean)

    fun evaluate(
        stiffness: Double = 420.0,
        dampingRatio: Double = 0.88,
        initialDisplacement: Double = 1.0,
        timeSeconds: Double,
    ): SpringState {
        require(stiffness > 0.0 && dampingRatio in 0.1..1.0 && timeSeconds >= 0.0)
        val omega0 = sqrt(stiffness)
        val pos: Double
        val vel: Double
        if (dampingRatio >= 0.9999) {
            // Critically damped: x(t) = (x0 + omega0*x0*t) * e^(-omega0*t)
            val e = exp(-omega0 * timeSeconds)
            pos = (initialDisplacement + omega0 * initialDisplacement * timeSeconds) * e
            vel = -(omega0 * omega0 * initialDisplacement * timeSeconds) * e
        } else {
            val omegaD = omega0 * sqrt(1.0 - dampingRatio * dampingRatio)
            val env = exp(-dampingRatio * omega0 * timeSeconds)
            val c1 = initialDisplacement
            val c2 = (dampingRatio * omega0 * initialDisplacement) / omegaD
            pos = env * (c1 * cos(omegaD * timeSeconds) + c2 * sin(omegaD * timeSeconds))
            vel = -dampingRatio * omega0 * pos + env * (-c1 * omegaD * sin(omegaD * timeSeconds) + c2 * omegaD * cos(omegaD * timeSeconds))
        }
        val settled = kotlin.math.abs(pos) < 0.001 && kotlin.math.abs(vel) < 0.01
        return SpringState(if (settled) 0.0 else pos, if (settled) 0.0 else vel, settled)
    }
}
