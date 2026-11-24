// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

/**
 * Bounded WGS84 geographic bounding box clamper and validator.
 * Clamps latitudes to [-90.0, 90.0] and longitudes to [-180.0, 180.0],
 * and validates `minLat <= maxLat`.
 */
class GeoBoundingBoxClamper private constructor() {
    companion object {
        const val MIN_LAT = -90.0
        const val MAX_LAT = 90.0
        const val MIN_LON = -180.0
        const val MAX_LON = 180.0

        data class BBox(
            val minLat: Double,
            val minLon: Double,
            val maxLat: Double,
            val maxLon: Double
        ) {
            fun latSpan(): Double = maxLat - minLat
            fun lonSpan(): Double = if (maxLon >= minLon) maxLon - minLon else (360.0 - minLon + maxLon)
        }

        fun clamp(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): BBox {
            require(!minLat.isNaN() && !minLon.isNaN() && !maxLat.isNaN() && !maxLon.isNaN()) {
                "NaN coordinate"
            }
            val cMinLat = minLat.coerceIn(MIN_LAT, MAX_LAT)
            val cMaxLat = maxLat.coerceIn(MIN_LAT, MAX_LAT)
            require(cMinLat <= cMaxLat) { "minLat > maxLat after clamp" }
            val cMinLon = minLon.coerceIn(MIN_LON, MAX_LON)
            val cMaxLon = maxLon.coerceIn(MIN_LON, MAX_LON)
            return BBox(cMinLat, cMinLon, cMaxLat, cMaxLon)
        }
    }
}
