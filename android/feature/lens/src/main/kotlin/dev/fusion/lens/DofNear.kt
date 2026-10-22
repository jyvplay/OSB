// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Depth-of-field near/far limits via the hyperfocal distance.
 *
 * Sits next to [EvShift] (exposure pairs) and [ZoomLadder] (focal steps):
 * nothing computes where acceptable sharpness starts and ends. From focal
 * length f, f-number N, circle of confusion c (all millimeters), and focus
 * distance s, the hyperfocal distance H = f^2/(N·c) + f anchors the thin-
 * lens near/far formulas; at or past H the far limit is infinity,
 * returned as null rather than a huge number, because "far = 1e18mm" in a
 * UI would be a lie with precision. All inputs must be finite and
 * positive; near is always finite for positive focus distances, and
 * callers convert units — millimeters in, millimeters out, documented so
 * no caller mixes meters into one slot. Diffraction, focus breathing, and
 * real-lens corrections are outside: thin-lens math only.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object DofNear {

    /** Depth-of-field limits in millimeters; far null means infinity. */
    data class Limits(val near: Double, val far: Double?)

    /**
     * Hyperfocal distance (mm) for focal length [fMm], f-number [n], and
     * circle of confusion [cocMm].
     */
    fun hyperfocal(fMm: Double, n: Double, cocMm: Double): Double {
        require(fMm.isFinite() && fMm > 0.0) { "bad focal" }
        require(n.isFinite() && n > 0.0) { "bad aperture" }
        require(cocMm.isFinite() && cocMm > 0.0) { "bad coc" }
        return fMm * fMm / (n * cocMm) + fMm
    }

    /**
     * Near/far limits (mm) at focus distance [sMm]. Far is null at or
     * past the hyperfocal distance.
     */
    fun limits(fMm: Double, n: Double, cocMm: Double, sMm: Double): Limits {
        require(sMm.isFinite() && sMm > 0.0) { "bad focus" }
        val h = hyperfocal(fMm, n, cocMm)
        val near = sMm * (h - fMm) / (h + sMm - 2 * fMm)
        require(near.isFinite() && near > 0.0) { "near out of range" }
        if (sMm >= h) return Limits(near, null)
        val far = sMm * (h - fMm) / (h - sMm)
        require(far.isFinite() && far > 0.0) { "far out of range" }
        return Limits(near, far)
    }
}
