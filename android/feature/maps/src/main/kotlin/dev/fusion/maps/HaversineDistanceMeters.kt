// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.*

/**
 * Bounded WGS84 Haversine great-circle distance calculator in meters.
 */
class HaversineDistanceMeters private constructor() {
    companion object {
        const val EARTH_RADIUS_M = 6_371_000.0

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            require(lat1 in -90.0..90.0 && lat2 in -90.0..90.0) { "latitude out of bounds" }
            require(lon1 in -180.0..180.0 && lon2 in -180.0..180.0) { "longitude out of bounds" }
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val a = sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
            return EARTH_RADIUS_M * c
        }
    }
}
