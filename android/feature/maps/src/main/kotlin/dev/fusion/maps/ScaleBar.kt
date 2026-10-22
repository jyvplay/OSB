// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.cos
import kotlin.math.pow

/**
 * Web-Mercator scale-bar math (meters per pixel + nice lengths).
 *
 * Sits next to [TileMath] (tiles) and [TileCover] (coverage): neither
 * answers "how many meters is 100 screen pixels here" for the offline
 * map's scale bar. Ground resolution follows the standard
 * 156543.03392·cos(lat)/2^z; [bar] then picks the largest 1/2/5×10^n
 * length fitting a target pixel width, returning both the meters and the
 * exact pixel length so the UI draws a truthful bar. Latitude beyond the
 * Mercator limit is refused (not clamped — a scale bar must not silently
 * compute for a projection that cannot show that latitude), and zoom
 * mirrors [TileMath]'s 0..19. Doubles throughout; sub-millimeter bars are
 * the caller's rounding policy.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object ScaleBar {
    const val MAX_ZOOM = 19
    const val MAX_LAT = 85.05112878
    const val EQUATOR_MPP = 156543.03392

    /** A scale bar: real-world meters plus its exact pixel length. */
    data class Bar(val meters: Double, val pixels: Double)

    /**
     * Meters per pixel at [lat] degrees and zoom [z].
     *
     * @throws IllegalArgumentException on out-of-range latitude/zoom.
     */
    fun metersPerPixel(lat: Double, z: Int): Double {
        require(lat.isFinite() && lat in -MAX_LAT..MAX_LAT) { "lat out of range" }
        require(z in 0..MAX_ZOOM) { "zoom must be 0..$MAX_ZOOM" }
        return EQUATOR_MPP * cos(Math.toRadians(lat)) / 2.0.pow(z)
    }

    /**
     * Largest 1/2/5-series length (meters) fitting [targetPx] pixels at
     * [mpp] meters per pixel.
     *
     * @throws IllegalArgumentException on non-positive inputs.
     */
    fun bar(mpp: Double, targetPx: Double): Bar {
        require(mpp.isFinite() && mpp > 0.0) { "bad mpp" }
        require(targetPx.isFinite() && targetPx > 0.0) { "bad target" }
        val maxMeters = mpp * targetPx
        var pow10 = 1.0
        while (pow10 * 10.0 <= maxMeters) pow10 *= 10.0
        while (pow10 > maxMeters) pow10 /= 10.0
        var best = pow10
        for (mult in listOf(2.0, 5.0, 10.0)) {
            if (pow10 * mult <= maxMeters) best = pow10 * mult
        }
        return Bar(best, best / mpp)
    }
}
