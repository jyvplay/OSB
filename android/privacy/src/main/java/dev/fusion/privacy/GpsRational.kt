package dev.fusion.privacy

/**
 * EXIF GPS rational conversion (GPSLatitude/GPSLongitude are THREE
 * unsigned rationals deg/min/sec; refs N/S/E/W sign them) — the pure
 * math JpegSeg's segment walking needs AFTER extraction, kept in
 * :privacy because its product purpose is the GPS-presence audit in
 * exports.
 *
 * HONESTY: converting coordinates is not removing them — the privacy
 * action is the caller's strip step; this kernel makes what WOULD
 * leak legible so consent screens can show it.
 *
 * Declared narrowings: zero denominators refuse (corrupt EXIF, not
 * zero); minutes/seconds >= 60 refuse; the degrees/1 + minutes-with-
 * fraction form (common in practice) is accepted.
 */
object GpsRational {
    const val SCOPE = "deg/min/sec rational math for the GPS-presence audit; stripping is the caller's act."

    data class Rat(val num: Long, val den: Long) {
        init {
            require(den != 0L) { "zero denominator (corrupt EXIF)" }
            require(num >= 0 && den > 0) { "GPS rationals are unsigned" }
        }
        val value: Double get() = num.toDouble() / den
    }

    fun toDecimal(deg: Rat, min: Rat, sec: Rat, ref: Char): Double {
        require(ref in charArrayOf('N', 'S', 'E', 'W')) { "ref '" + ref + "'" }
        require(min.value < 60.0 && sec.value < 60.0) { "minutes/seconds must be below 60" }
        val v = deg.value + min.value / 60.0 + sec.value / 3600.0
        val max = if (ref == 'N' || ref == 'S') 90.0 else 180.0
        require(v <= max + 1e-9) { "coordinate " + v + " exceeds " + max }
        return if (ref == 'S' || ref == 'W') -v else v
    }

    /** Presence verdict for consent UI: any nonzero component counts. */
    fun isNonZero(deg: Rat, min: Rat, sec: Rat): Boolean =
        deg.num != 0L || min.num != 0L || sec.num != 0L
}
