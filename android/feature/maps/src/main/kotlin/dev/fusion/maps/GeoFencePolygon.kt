// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline **Polygon Geofence Evaluator** (Jordan Curve Ray-Casting with Interior Exclusion Holes +
 * Haversine Boundary Distance).
 */
object GeoFencePolygon {
    const val SCOPE =
        "Offline ray-casting point-in-polygon geofence evaluator (outer ring + exclusion holes + Haversine meters distance)."

    private const val EARTH_RADIUS_M = 6_371_008.8

    data class LatLon(val latDeg: Double, val lonDeg: Double) {
        init {
            require(latDeg in -90.0..90.0 && lonDeg in -180.0..180.0) { "invalid coordinate ($latDeg, $lonDeg)" }
        }
    }

    fun contains(outerRing: List<LatLon>, point: LatLon, holes: List<List<LatLon>> = emptyList()): Boolean {
        require(outerRing.size >= 3) { "polygon ring requires >= 3 vertices" }
        if (!rayCastInRing(outerRing, point)) return false
        return holes.none { hole -> hole.size >= 3 && rayCastInRing(hole, point) }
    }

    fun haversineMeters(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.latDeg - a.latDeg)
        val dLon = Math.toRadians(b.lonDeg - a.lonDeg)
        val lat1 = Math.toRadians(a.latDeg)
        val lat2 = Math.toRadians(b.latDeg)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt((1.0 - h).coerceAtLeast(0.0)))
    }

    private fun rayCastInRing(ring: List<LatLon>, p: LatLon): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val yi = ring[i].latDeg; val xi = ring[i].lonDeg
            val yj = ring[j].latDeg; val xj = ring[j].lonDeg
            val intersects = ((yi > p.latDeg) != (yj > p.latDeg)) &&
                (p.lonDeg < (xj - xi) * (p.latDeg - yi) / (yj - yi) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}
