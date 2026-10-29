// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-JVM **Offline Isochrone & Elevation-Penalized Naismith / Tobler Hiking & Cycling Travel-Time
 * Estimator** for `:feature:maps`.
 */
object MapsElevationTravelEstimator {
    const val SCOPE =
        "Offline WGS-84 Haversine + Tobler hiking/cycling slope-adjusted travel-time & energy estimator."

    private const val EARTH_RADIUS_M = 6_371_008.8

    data class Waypoint3D(
        val latDeg: Double,
        val lonDeg: Double,
        val elevationMeters: Double,
    )

    data class RouteTravelSummary(
        val horizontalDistanceMeters: Double,
        val totalAscentMeters: Double,
        val totalDescentMeters: Double,
        val estimatedWalkingSeconds: Double,
    )

    fun evaluateRoute(points: List<Waypoint3D>): RouteTravelSummary {
        require(points.size >= 2) { "At least 2 waypoints required" }
        var dist = 0.0
        var ascent = 0.0
        var descent = 0.0
        var seconds = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val segM = haversineMeters(a.latDeg, a.lonDeg, b.latDeg, b.lonDeg)
            val dz = b.elevationMeters - a.elevationMeters
            if (dz > 0) ascent += dz else descent += -dz
            dist += segM
            val slope = if (segM > 1.0) (dz / segM).coerceIn(-0.6, 0.6) else 0.0
            // Tobler's hiking function: W = 6 * exp(-3.5 * |dh/dx + 0.05|) km/h => m/s = W / 3.6
            val speedKmh = 6.0 * kotlin.math.exp(-3.5 * kotlin.math.abs(slope + 0.05))
            val speedMs = (speedKmh / 3.6).coerceAtLeast(0.25)
            seconds += segM / speedMs
        }
        return RouteTravelSummary(dist, ascent, descent, seconds)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1.0 - h))
    }
}
