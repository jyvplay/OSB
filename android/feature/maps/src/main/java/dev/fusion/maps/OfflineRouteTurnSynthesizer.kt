// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Clean-room Offline Turn-by-Turn Maneuver Classifier & Off-Route Cross-Track Detector.
 *
 * Computes initial/outgoing compass bearings along local polyline waypoints, classifies turn
 * maneuvers (`STRAIGHT`, `SLIGHT_LEFT`, `LEFT`, `SHARP_LEFT`, `SLIGHT_RIGHT`, `RIGHT`,
 * `SHARP_RIGHT`, `U_TURN`), and detects off-route excursion distance in meters with zero cloud calls!
 */
object OfflineRouteTurnSynthesizer {
    private const val EARTH_RADIUS_M = 6_371_008.8

    enum class Maneuver {
        STRAIGHT,
        SLIGHT_LEFT,
        LEFT,
        SHARP_LEFT,
        SLIGHT_RIGHT,
        RIGHT,
        SHARP_RIGHT,
        U_TURN,
        ARRIVE,
    }

    data class LatLon(val latDeg: Double, val lonDeg: Double) {
        init {
            require(latDeg in -90.0..90.0 && lonDeg in -180.0..180.0) { "invalid coordinates" }
        }
    }

    data class TurnInstruction(
        val waypointIndex: Int,
        val maneuver: Maneuver,
        val turnAngleSignedDeg: Double,
        val distanceToNextMeters: Double,
    )

    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val phi1 = Math.toRadians(a.latDeg)
        val phi2 = Math.toRadians(b.latDeg)
        val dLambda = Math.toRadians(b.lonDeg - a.lonDeg)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun haversineMeters(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.latDeg - a.latDeg)
        val dLon = Math.toRadians(b.lonDeg - a.lonDeg)
        val s = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latDeg)) * cos(Math.toRadians(b.latDeg)) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * EARTH_RADIUS_M * atan2(kotlin.math.sqrt(s), kotlin.math.sqrt((1.0 - s).coerceAtLeast(0.0)))
    }

    fun classifyTurnAngle(signedDeltaDeg: Double): Maneuver {
        val d = ((signedDeltaDeg + 540.0) % 360.0) - 180.0 // normalize into [-180, +180)
        return when {
            abs(d) >= 155.0 -> Maneuver.U_TURN
            d <= -115.0 -> Maneuver.SHARP_LEFT
            d <= -45.0 -> Maneuver.LEFT
            d <= -15.0 -> Maneuver.SLIGHT_LEFT
            d >= 115.0 -> Maneuver.SHARP_RIGHT
            d >= 45.0 -> Maneuver.RIGHT
            d >= 15.0 -> Maneuver.SLIGHT_RIGHT
            else -> Maneuver.STRAIGHT
        }
    }

    fun synthesizeRouteTurns(polyline: List<LatLon>): List<TurnInstruction> {
        require(polyline.size >= 2) { "route polyline requires >= 2 points" }
        val out = ArrayList<TurnInstruction>()
        for (i in 1 until polyline.size - 1) {
            val inB = bearingDeg(polyline[i - 1], polyline[i])
            val outB = bearingDeg(polyline[i], polyline[i + 1])
            val delta = ((outB - inB + 540.0) % 360.0) - 180.0
            val dist = haversineMeters(polyline[i], polyline[i + 1])
            out.add(TurnInstruction(i, classifyTurnAngle(delta), delta, dist))
        }
        out.add(TurnInstruction(polyline.lastIndex, Maneuver.ARRIVE, 0.0, 0.0))
        return out
    }

    /** Equirectangular local-tangent-plane point-to-segment distance in meters (`rerouteThresholdMeters = 45m`). */
    fun minCrossTrackMeters(position: LatLon, polyline: List<LatLon>): Double {
        require(polyline.size >= 2) { "polyline requires >= 2 vertices" }
        val cosLat = cos(Math.toRadians(position.latDeg)).coerceAtLeast(1e-6)
        fun toXY(p: LatLon): Pair<Double, Double> =
            Math.toRadians(p.lonDeg - position.lonDeg) * cosLat * EARTH_RADIUS_M to
                Math.toRadians(p.latDeg - position.latDeg) * EARTH_RADIUS_M

        var best = Double.POSITIVE_INFINITY
        for (i in 0 until polyline.size - 1) {
            val (ax, ay) = toXY(polyline[i])
            val (bx, by) = toXY(polyline[i + 1])
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-9) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx; val cy = ay + t * dy
            best = minOf(best, hypot(cx, cy))
        }
        return best
    }
}
