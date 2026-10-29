// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Clean-room analytical closed-form damped harmonic spring solver (`m = 1`) with accessibility
 * `reducedMotion` instant-snap compliance.
 *
 * Solves `x''(t) + 2 * zeta * omega0 * x'(t) + omega0^2 * (x(t) - target) = 0` (`omega0 = sqrt(stiffness)`)
 * analytically across all three damping regimes (`zeta < 1` underdamped, `zeta == 1` critically
 * damped, `zeta > 1` overdamped) so state evaluation at any `dtSeconds >= 0` is drift-free and
 * time-subdivision invariant (`step(t1 + t2) == step(step(t1), t2)` within IEEE-754 rounding).
 */
object SpringStep {
    const val SCOPE =
        "Closed-form unit-mass damped harmonic oscillator (under/critical/over-damped) with reducedMotion instant snap; multi-dimensional vector springs compose per axis."

    data class Spec(
        val stiffness: Double = 400.0,
        val dampingRatio: Double = 1.0,
        val positionThreshold: Double = 0.5e-3,
        val velocityThreshold: Double = 1e-2,
    ) {
        init {
            require(stiffness.isFinite() && stiffness > 0.0) { "stiffness $stiffness must be > 0" }
            require(dampingRatio.isFinite() && dampingRatio > 0.0) {
                "dampingRatio $dampingRatio must be > 0 (undamped perpetual oscillation refused)"
            }
            require(positionThreshold > 0.0 && velocityThreshold > 0.0) { "thresholds must be > 0" }
        }
    }

    data class State(val value: Double, val velocity: Double, val settled: Boolean)

    fun evaluate(
        spec: Spec,
        initialValue: Double,
        initialVelocity: Double,
        targetValue: Double,
        dtSeconds: Double,
        reducedMotion: Boolean = false,
    ): State {
        require(initialValue.isFinite() && initialVelocity.isFinite() && targetValue.isFinite()) {
            "non-finite spring state"
        }
        require(dtSeconds.isFinite() && dtSeconds >= 0.0) { "dtSeconds $dtSeconds must be >= 0" }
        if (reducedMotion) {
            return State(targetValue, 0.0, settled = true)
        }
        if (dtSeconds == 0.0) {
            val done = abs(initialValue - targetValue) <= spec.positionThreshold &&
                abs(initialVelocity) <= spec.velocityThreshold
            return if (done) State(targetValue, 0.0, true) else State(initialValue, initialVelocity, false)
        }

        val x0 = initialValue - targetValue
        val v0 = initialVelocity
        val omega0 = sqrt(spec.stiffness)
        val zeta = spec.dampingRatio

        val (xt, vt) = when {
            abs(zeta - 1.0) < 1e-7 -> {
                // Critically damped: x(t) = (A + B t) e^{-omega0 t}
                val env = exp(-omega0 * dtSeconds)
                val b = v0 + omega0 * x0
                val x = (x0 + b * dtSeconds) * env
                val v = (b - omega0 * (x0 + b * dtSeconds)) * env
                x to v
            }
            zeta < 1.0 -> {
                // Underdamped: x(t) = e^{-zeta omega0 t} (x0 cos(omegaD t) + ((v0 + zeta omega0 x0)/omegaD) sin(omegaD t))
                val omegaD = omega0 * sqrt(1.0 - zeta * zeta)
                val env = exp(-zeta * omega0 * dtSeconds)
                val c1 = x0
                val c2 = (v0 + zeta * omega0 * x0) / omegaD
                val cosT = cos(omegaD * dtSeconds)
                val sinT = sin(omegaD * dtSeconds)
                val x = env * (c1 * cosT + c2 * sinT)
                val v = -zeta * omega0 * x + env * (-c1 * omegaD * sinT + c2 * omegaD * cosT)
                x to v
            }
            else -> {
                // Overdamped: roots r1, r2 = -omega0 (zeta -/+ sqrt(zeta^2 - 1))
                val gamma = omega0 * sqrt(zeta * zeta - 1.0)
                val r1 = -zeta * omega0 + gamma
                val r2 = -zeta * omega0 - gamma
                val c2 = (v0 - r1 * x0) / (r2 - r1)
                val c1 = x0 - c2
                val e1 = exp(r1 * dtSeconds)
                val e2 = exp(r2 * dtSeconds)
                val x = c1 * e1 + c2 * e2
                val v = c1 * r1 * e1 + c2 * r2 * e2
                x to v
            }
        }

        val settled = abs(xt) <= spec.positionThreshold && abs(vt) <= spec.velocityThreshold
        return if (settled) {
            State(targetValue, 0.0, settled = true)
        } else {
            State(targetValue + xt, vt, settled = false)
        }
    }
}
