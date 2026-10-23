// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.atan

/**
 * Field-of-view math (focal length + sensor dimension to degrees).
 *
 * Sits next to [ZoomLadder] (focal steps), [EvShift] (exposure), and
 * [DofNear] (focus limits): nothing computes how WIDE the frame is.
 * [fov] applies FOV = 2·atan(d / 2f) for one sensor dimension (call
 * once per axis — diagonal/horizontal/vertical is the caller's choice
 * of d); [equiv] scales a focal length by a crop factor to its 35mm
 * equivalent. Rectilinear lenses only: fisheye projection, distortion,
 * and focus breathing are outside, and inputs are millimeters in,
 * degrees out, documented so no caller mixes units.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object FovMath {

    /**
     * Field of view in degrees for focal length [fMm] and one sensor
     * dimension [sensorMm].
     */
    fun fov(fMm: Double, sensorMm: Double): Double {
        require(fMm.isFinite() && fMm > 0.0) { "bad focal" }
        require(sensorMm.isFinite() && sensorMm > 0.0) { "bad sensor" }
        val v = 2.0 * atan(sensorMm / (2.0 * fMm)) * 180.0 / Math.PI
        require(v.isFinite()) { "fov out of range" }
        return v
    }

    /**
     * 35mm-equivalent focal length for [fMm] at crop factor [crop].
     */
    fun equiv(fMm: Double, crop: Double): Double {
        require(fMm.isFinite() && fMm > 0.0) { "bad focal" }
        require(crop.isFinite() && crop > 0.0) { "bad crop" }
        val v = fMm * crop
        require(v.isFinite()) { "equiv out of range" }
        return v
    }
}
