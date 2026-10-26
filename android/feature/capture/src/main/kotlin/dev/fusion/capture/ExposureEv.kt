package dev.fusion.capture

/**
 * Photographic exposure value arithmetic (APEX relations; ISO 2720:1974
 * exposure-meter geometry is the historical source of the constants).
 *
 * Definitions implemented: EV = log2(N^2 / t) for relative aperture N and
 * shutter time t seconds; the ISO-adjusted value EV_S = EV_100 +
 * log2(S/100) relates a meter reading at ISO S to the ISO-100 scale. Two
 * exposures are equivalent iff their EV (same ISO) are equal.
 *
 * Declared narrowings: pure arithmetic on caller-provided N/t/ISO — no
 * camera control, no metering, no scene luminance (the K-factor
 * calibration constant is deliberately out of scope); nominal third-stop
 * shutter/aperture markings are rounded values and equality is tested
 * with an explicit tolerance, never exact.
 */
object ExposureEv {
    const val SCOPE = "APEX EV arithmetic only; no metering, no camera control, no K-factor luminance claims."

    fun ev(aperture: Double, shutterSeconds: Double): Double {
        require(aperture > 0.0 && aperture.isFinite()) { "aperture $aperture" }
        require(shutterSeconds > 0.0 && shutterSeconds.isFinite()) { "shutter $shutterSeconds" }
        return log2(aperture * aperture / shutterSeconds)
    }

    /** Reading taken at [iso], expressed on the ISO-100 EV scale. */
    fun ev100(aperture: Double, shutterSeconds: Double, iso: Double): Double {
        require(iso > 0.0 && iso.isFinite()) { "iso $iso" }
        return ev(aperture, shutterSeconds) - log2(iso / 100.0)
    }

    /** Shutter time that keeps [targetEv] at the given aperture. */
    fun shutterFor(targetEv: Double, aperture: Double): Double {
        require(aperture > 0.0 && aperture.isFinite()) { "aperture $aperture" }
        return aperture * aperture / Math.pow(2.0, targetEv)
    }

    /** Aperture that keeps [targetEv] at the given shutter time. */
    fun apertureFor(targetEv: Double, shutterSeconds: Double): Double {
        require(shutterSeconds > 0.0 && shutterSeconds.isFinite()) { "shutter $shutterSeconds" }
        return Math.sqrt(Math.pow(2.0, targetEv) * shutterSeconds)
    }

    fun equivalent(n1: Double, t1: Double, n2: Double, t2: Double, toleranceEv: Double = 0.05): Boolean {
        require(toleranceEv >= 0.0) { "tolerance $toleranceEv" }
        return kotlin.math.abs(ev(n1, t1) - ev(n2, t2)) <= toleranceEv
    }

    private fun log2(x: Double): Double = Math.log(x) / Math.log(2.0)
}
