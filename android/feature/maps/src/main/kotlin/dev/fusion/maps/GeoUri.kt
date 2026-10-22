// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

/** Parsed geo URI: WGS-84 lat/lon with optional altitude and uncertainty. */
data class GeoPoint(val lat: Double, val lon: Double, val alt: Double?, val unc: Double?)

/**
 * RFC 5870 `geo:` subset: `geo:lat,lon[,alt][;crs=wgs84][;u=unc]`.
 * Strict where the RFC is lenient: only `crs=wgs84` is admitted and
 * unknown parameters are refused (never silently ignored). Coordinates
 * must be finite and in range. Formatting round-trips through Double
 * printing; `;u=` survives only when present.
 */
object GeoUri {
    fun parse(text: String): GeoPoint {
        require(text.length <= 512) { "uri over 512 chars" }
        require(text.startsWith("geo:", ignoreCase = true)) { "missing geo: scheme" }
        val rest = text.substring(4)
        require(rest.isNotEmpty() && !rest.any { it.isWhitespace() }) { "bad uri body" }
        val parts = rest.split(';')
        val coords = parts[0].split(',')
        require(coords.size == 2 || coords.size == 3) { "need lat,lon[,alt]" }
        val lat = coords[0].toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: throw IllegalArgumentException("bad lat")
        val lon = coords[1].toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: throw IllegalArgumentException("bad lon")
        require(lat in -90.0..90.0) { "lat $lat outside -90..90" }
        require(lon in -180.0..180.0) { "lon $lon outside -180..180" }
        val alt = if (coords.size == 3) {
            coords[2].toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("bad alt")
        } else null
        var unc: Double? = null
        for (p in parts.drop(1)) {
            val kv = p.split('=', limit = 2)
            require(kv.size == 2) { "bad parameter $p" }
            when (kv[0].lowercase()) {
                "crs" -> require(kv[1].equals("wgs84", ignoreCase = true)) { "only crs=wgs84 admitted" }
                "u" -> {
                    unc = kv[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                        ?: throw IllegalArgumentException("bad u")
                }
                else -> throw IllegalArgumentException("unknown parameter ${kv[0]}")
            }
        }
        return GeoPoint(lat, lon, alt, unc)
    }

    fun format(p: GeoPoint): String {
        require(p.lat in -90.0..90.0 && p.lon in -180.0..180.0) { "coords out of range" }
        require(p.lat.isFinite() && p.lon.isFinite()) { "non-finite coords" }
        if (p.alt != null) require(p.alt.isFinite()) { "non-finite alt" }
        if (p.unc != null) require(p.unc.isFinite() && p.unc >= 0) { "bad unc" }
        return buildString {
            append("geo:")
            append(p.lat)
            append(',')
            append(p.lon)
            if (p.alt != null) {
                append(',')
                append(p.alt)
            }
            if (p.unc != null) {
                append(";u=")
                append(p.unc)
            }
        }
    }
}
