package dev.fusion.maps

/**
 * Decimal degrees <-> degrees/minutes/seconds formatting for coordinates.
 *
 * Conventions: latitude hemisphere N/S (|lat| <= 90), longitude E/W
 * (|lon| <= 180); minutes and seconds are 0..59 with seconds carrying
 * the fraction; formatting rounds seconds to the requested precision
 * and CARRIES overflow (59.999" at precision 0 becomes the next
 * minute, never 60"). Parsing refuses minutes/seconds >= 60.
 *
 * Declared narrowings: DMS text in the plain `D°M′S″H` shape only — no
 * cardinal-first forms, no decimal-minutes (GPS NMEA) shape, no locale
 * digit sets. Round-tripping is exact to the formatted precision, and
 * the tests state the tolerance explicitly.
 */
object DmsFormat {
    const val SCOPE = "Plain D°M'S\"H shape only; carry-on-round enforced; NMEA decimal-minutes out of scope."

    data class Dms(val degrees: Int, val minutes: Int, val seconds: Double, val negative: Boolean)

    fun split(value: Double, maxAbs: Double): Dms {
        require(value.isFinite() && kotlin.math.abs(value) <= maxAbs) { "coordinate $value outside ±$maxAbs" }
        val neg = value < 0
        val abs = kotlin.math.abs(value)
        val deg = abs.toInt()
        val minFull = (abs - deg) * 60.0
        val min = minFull.toInt()
        val sec = (minFull - min) * 60.0
        return Dms(deg, min, sec, neg)
    }

    private fun carry(d: Dms, secRounded: Double): Triple<Int, Int, Double> {
        var deg = d.degrees; var min = d.minutes; var sec = secRounded
        if (sec >= 60.0) { sec -= 60.0; min += 1 }
        if (min >= 60) { min -= 60; deg += 1 }
        return Triple(deg, min, sec)
    }

    fun formatLat(lat: Double, secondsDecimals: Int = 0): String = format(lat, 90.0, if (lat < 0) "S" else "N", secondsDecimals)

    fun formatLon(lon: Double, secondsDecimals: Int = 0): String = format(lon, 180.0, if (lon < 0) "W" else "E", secondsDecimals)

    private fun format(v: Double, maxAbs: Double, hemi: String, dec: Int): String {
        require(dec in 0..4) { "seconds precision $dec outside 0..4" }
        val d = split(v, maxAbs)
        val factor = Math.pow(10.0, dec.toDouble())
        val rounded = Math.round(d.seconds * factor) / factor
        val (deg, min, sec) = carry(d, rounded)
        val secText = if (dec == 0) "%02.0f".format(sec) else "%0${3 + dec}.${dec}f".format(sec)
        return "%d\u00B0%02d\u2032%s\u2033%s".format(deg, min, secText, hemi)
    }

    /** Parse `D°M′S″H`; hemisphere letter decides the sign. */
    fun parse(text: String): Double {
        val m = Regex("(\\d{1,3})\u00B0(\\d{1,2})\u2032(\\d{1,2}(?:\\.\\d+)?)\u2033([NSEW])").matchEntire(text)
            ?: throw IllegalArgumentException("'$text' is not in D\u00B0M\u2032S\u2033H shape")
        val deg = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].toDouble()
        val hemi = m.groupValues[4]
        require(min < 60) { "minutes $min must be below 60" }
        require(sec < 60.0) { "seconds $sec must be below 60" }
        val maxDeg = if (hemi == "N" || hemi == "S") 90 else 180
        val value = deg + min / 60.0 + sec / 3600.0
        require(value <= maxDeg + 1e-12) { "coordinate $value exceeds $maxDeg for hemisphere $hemi" }
        return if (hemi == "S" || hemi == "W") -value else value
    }
}
