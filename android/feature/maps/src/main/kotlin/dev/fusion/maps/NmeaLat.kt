package dev.fusion.maps

/**
 * NMEA 0183 coordinate fields — the decimal-MINUTES shape [DmsFormat]
 * declared out of its scope: latitude `ddmm.mmmm` + N/S, longitude
 * `dddmm.mmmm` + E/W (GGA/RMC sentences carry these).
 *
 * Conversion: degrees + minutes/60, sign from hemisphere. Formatting
 * emits four minute decimals (the common receiver precision).
 *
 * Declared narrowings: the coordinate FIELDS only — no sentence
 * framing, no checksum (a sentence-level kernel would own `$...*hh`),
 * no altitude/speed fields.
 */
object NmeaLat {
    const val SCOPE = "ddmm.mmmm field conversion only; sentence framing and checksums are a different layer."

    fun parseLat(field: String, hemi: Char): Double {
        require(hemi == 'N' || hemi == 'S') { "latitude hemisphere '$hemi'" }
        require(Regex("\\d{4}\\.\\d+").matches(field)) { "latitude field '$field' is not ddmm.mmmm" }
        val deg = field.substring(0, 2).toInt()
        val min = field.substring(2).toDouble()
        require(min < 60.0) { "minutes $min must be below 60" }
        val v = deg + min / 60.0
        require(v <= 90.0) { "latitude $v exceeds 90" }
        return if (hemi == 'S') -v else v
    }

    fun parseLon(field: String, hemi: Char): Double {
        require(hemi == 'E' || hemi == 'W') { "longitude hemisphere '$hemi'" }
        require(Regex("\\d{5}\\.\\d+").matches(field)) { "longitude field '$field' is not dddmm.mmmm" }
        val deg = field.substring(0, 3).toInt()
        val min = field.substring(3).toDouble()
        require(min < 60.0) { "minutes $min must be below 60" }
        val v = deg + min / 60.0
        require(v <= 180.0) { "longitude $v exceeds 180" }
        return if (hemi == 'W') -v else v
    }

    fun formatLat(lat: Double): Pair<String, Char> {
        require(lat.isFinite() && kotlin.math.abs(lat) <= 90.0) { "latitude $lat" }
        val abs = kotlin.math.abs(lat)
        val deg = abs.toInt()
        val min = (abs - deg) * 60.0
        return "%02d%07.4f".format(deg, min) to (if (lat < 0) 'S' else 'N')
    }

    fun formatLon(lon: Double): Pair<String, Char> {
        require(lon.isFinite() && kotlin.math.abs(lon) <= 180.0) { "longitude $lon" }
        val abs = kotlin.math.abs(lon)
        val deg = abs.toInt()
        val min = (abs - deg) * 60.0
        return "%03d%07.4f".format(deg, min) to (if (lon < 0) 'W' else 'E')
    }
}
