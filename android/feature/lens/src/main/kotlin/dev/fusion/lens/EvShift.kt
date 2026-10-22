// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.log2
import kotlin.math.pow

/**
 * Exposure-value (EV) arithmetic for equivalent-exposure shifts.
 *
 * Sits next to [ZoomLadder] (focal-length steps): nothing computes the
 * exposure equation EV = log2(N^2 / t) or solves it for a missing side when
 * the photographer shifts aperture against shutter speed. Given any two of
 * aperture (f-number N), shutter time (seconds t), and EV, this derives the
 * third; [shift] answers "I want f/8 instead of f/2.8 at the same exposure —
 * what shutter?". Inputs must be finite and positive (an f-number or shutter
 * time of zero is physically meaningless, not a default). Results are Doubles
 * and therefore approximate: callers must round to their camera's real
 * third-stop ladder rather than programming 1/96.3s. ISO is deliberately
 * outside — this is the aperture/shutter pair only, documented so no caller
 * mistakes EV here for an ISO-aware meter reading.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object EvShift {

    /**
     * Exposure value for f-number [aperture] and shutter [shutterSec].
     *
     * @throws IllegalArgumentException on non-finite or non-positive inputs.
     */
    fun ev(aperture: Double, shutterSec: Double): Double {
        require(aperture.isFinite() && aperture > 0.0) { "bad aperture" }
        require(shutterSec.isFinite() && shutterSec > 0.0) { "bad shutter" }
        return log2(aperture * aperture / shutterSec)
    }

    /** Shutter time (seconds) holding [ev] at f-number [aperture]. */
    fun shutterFor(aperture: Double, ev: Double): Double {
        require(aperture.isFinite() && aperture > 0.0) { "bad aperture" }
        require(ev.isFinite()) { "bad ev" }
        return aperture * aperture / 2.0.pow(ev)
    }

    /** F-number holding [ev] at shutter [shutterSec]. */
    fun apertureFor(shutterSec: Double, ev: Double): Double {
        require(shutterSec.isFinite() && shutterSec > 0.0) { "bad shutter" }
        require(ev.isFinite()) { "bad ev" }
        return kotlin.math.sqrt(shutterSec * 2.0.pow(ev))
    }

    /**
     * Equivalent shutter when moving from [fromAperture] to [toAperture] at
     * the exposure implied by ([fromAperture], [shutterSec]).
     */
    fun shift(fromAperture: Double, shutterSec: Double, toAperture: Double): Double =
        shutterFor(toAperture, ev(fromAperture, shutterSec))
}
