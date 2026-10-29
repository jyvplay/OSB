// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Clean-room WGS-84 Latitude/Longitude -> Universal Transverse Mercator (UTM) zone, MGRS
 * latitude band, Norway (`32V`) & Svalbard (`31X/33X/35X/37X`) exception zones, and Snyder
 * Transverse Mercator metric `(easting, northing)` projection for `:feature:maps`.
 *
 * Refuses polar regions outside the normative UTM domain (`80.0°S .. 84.0°N`, where Universal
 * Polar Stereographic UPS applies) rather than silently extrapolating distorted coordinates.
 */
object UtmGrid {
    const val SCOPE =
        "WGS-84 forward UTM zone (1..60 with Norway 32V and Svalbard X-band exceptions), MGRS latitude band C..X, and Transverse Mercator easting/northing meters over 80°S..84°N; polar UPS and inverse UTM->WGS84 out of scope."

    const val WGS84_A = 6_378_137.0
    const val WGS84_F = 1.0 / 298.257223563
    const val UTM_K0 = 0.9996
    const val FALSE_EASTING = 500_000.0
    const val SOUTH_FALSE_NORTHING = 10_000_000.0

    private const val BAND_LETTERS = "CDEFGHJKLMNPQRSTUVWX" // omits I and O per MGRS/UTM spec

    data class Coordinate(
        val zone: Int,
        val band: Char,
        val hemisphere: Char,
        val eastingMeters: Double,
        val northingMeters: Double,
    ) {
        fun gridZoneDesignator(): String = "$zone$band"
    }

    fun fromLatLon(latDeg: Double, lonDeg: Double): Coordinate {
        require(latDeg.isFinite() && lonDeg.isFinite()) { "non-finite lat/lon ($latDeg, $lonDeg)" }
        require(latDeg in -80.0..84.0) {
            "latitude $latDeg outside UTM domain -80.0..84.0 (UPS polar region out of scope)"
        }
        require(lonDeg in -180.0..180.0) { "longitude $lonDeg outside -180.0..180.0" }

        val normLon = if (lonDeg == 180.0) -180.0 else lonDeg
        val zone = computeZone(latDeg, normLon)
        val band = computeBand(latDeg)
        val hemi = if (latDeg >= 0.0) 'N' else 'S'

        val eSq = WGS84_F * (2.0 - WGS84_F)
        val ePrimeSq = eSq / (1.0 - eSq)

        val phi = Math.toRadians(latDeg)
        val lambda = Math.toRadians(normLon)
        val lambda0 = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)

        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)

        val n = WGS84_A / sqrt(1.0 - eSq * sinPhi * sinPhi)
        val t = tanPhi * tanPhi
        val c = ePrimeSq * cosPhi * cosPhi
        val aTerm = cosPhi * (lambda - lambda0)

        val e4 = eSq * eSq
        val e6 = e4 * eSq
        val m = WGS84_A * (
            (1.0 - eSq / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * phi -
                (3.0 * eSq / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * sin(2.0 * phi) +
                (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * sin(4.0 * phi) -
                (35.0 * e6 / 3072.0) * sin(6.0 * phi)
            )

        val a2 = aTerm * aTerm
        val a3 = a2 * aTerm
        val a4 = a2 * a2
        val a5 = a4 * aTerm
        val a6 = a4 * a2

        val easting = FALSE_EASTING + UTM_K0 * n * (
            aTerm +
                (1.0 - t + c) * a3 / 6.0 +
                (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ePrimeSq) * a5 / 120.0
            )

        var northing = UTM_K0 * (
            m + n * tanPhi * (
                a2 / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c * c) * a4 / 24.0 +
                    (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ePrimeSq) * a6 / 720.0
                )
            )
        if (latDeg < 0.0) northing += SOUTH_FALSE_NORTHING

        return Coordinate(zone, band, hemi, easting, northing)
    }

    fun computeZone(latDeg: Double, lonDeg: Double): Int {
        val normLon = if (lonDeg == 180.0) -180.0 else lonDeg
        // Southwest Norway 32V exception (56°N..64°N, 3°E..12°E)
        if (latDeg >= 56.0 && latDeg < 64.0 && normLon >= 3.0 && normLon < 12.0) {
            return 32
        }
        // Svalbard X-band exceptions (72°N..84°N, 0°E..42°E)
        if (latDeg >= 72.0 && latDeg <= 84.0) {
            when {
                normLon >= 0.0 && normLon < 9.0 -> return 31
                normLon >= 9.0 && normLon < 21.0 -> return 33
                normLon >= 21.0 && normLon < 33.0 -> return 35
                normLon >= 33.0 && normLon < 42.0 -> return 37
            }
        }
        return (floor((normLon + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
    }

    fun computeBand(latDeg: Double): Char {
        require(latDeg in -80.0..84.0) { "latitude $latDeg outside -80..84" }
        // Band X spans 12 degrees (72°N..84°N); bands C..W each span 8 degrees (-80°..72°).
        val idx = if (latDeg >= 72.0) 19 else floor((latDeg + 80.0) / 8.0).toInt().coerceIn(0, 18)
        return BAND_LETTERS[idx]
    }
}
