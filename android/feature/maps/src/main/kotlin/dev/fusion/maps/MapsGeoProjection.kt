// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.*

/**
 * Production-grade Haversine great-circle distance, initial bearing,
 * and Web Mercator Slippy-Map (x, y, zoom) tile projection.
 */
class MapsGeoProjection private constructor() {
    companion object {
        const val EARTH_RADIUS_M = 6_371_000.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
            return 2.0 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
        }

        fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)
            val y = sin(dLon) * cos(rLat2)
            val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }

        fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
            require(zoom in 0..22) { "zoom out of bounds" }
            val n = 1 shl zoom
            val x = (((lon + 180.0) / 360.0) * n).toInt().coerceIn(0, n - 1)
            val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
            val y = (((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0) * n).toInt().coerceIn(0, n - 1)
            return x to y
        }
    }
}
