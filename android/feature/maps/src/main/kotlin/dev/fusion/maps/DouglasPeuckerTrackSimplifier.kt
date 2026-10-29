// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Ramer–Douglas–Peucker perpendicular-metric GPS polyline simplifier + Tails-safe timestamp-scrubbed
 * GPX 1.1 exporter for Fusion Maps offline route sharing.
 */
object DouglasPeuckerTrackSimplifier {
    const val SCOPE =
        "Ramer-Douglas-Peucker equirectangular metre-error GPS track simplifier and timestamp-scrubbed GPX 1.1 exporter."

    data class GpsPoint(val latDeg: Double, val lonDeg: Double, val timestampIso: String? = null)

    fun simplifyTrack(points: List<GpsPoint>, epsilonMeters: Double = 5.0): List<GpsPoint> {
        require(epsilonMeters >= 0.0)
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true

        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.lastIndex)
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var maxDist = -1.0
            var maxIdx = -1
            for (i in (s + 1) until e) {
                val d = perpendicularMeters(points[i], points[s], points[e])
                if (d > maxDist) { maxDist = d; maxIdx = i }
            }
            if (maxDist > epsilonMeters && maxIdx > s) {
                keep[maxIdx] = true
                stack.addLast(s to maxIdx)
                stack.addLast(maxIdx to e)
            }
        }
        return points.filterIndexed { idx, _ -> keep[idx] }
    }

    fun exportPrivacyScrubbedGpx(points: List<GpsPoint>, trackName: String = "Sanitized Route"): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"FusionMapsPrivacy\"><trk><name>${trackName.replace("<", "")}</name><trkseg>\n")
        for (p in points) {
            // Deliberately omits <time> and rounds coordinates to 5 decimal places (~1.1 m)
            append("<trkpt lat=\"%.5f\" lon=\"%.5f\"/>\n".format(java.util.Locale.ROOT, p.latDeg, p.lonDeg))
        }
        append("</trkseg></trk></gpx>")
    }

    private fun perpendicularMeters(p: GpsPoint, a: GpsPoint, b: GpsPoint): Double {
        val mPerDegLat = 111_132.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians((a.latDeg + b.latDeg) * 0.5))
        val px = (p.lonDeg - a.lonDeg) * mPerDegLon
        val py = (p.latDeg - a.latDeg) * mPerDegLat
        val bx = (b.lonDeg - a.lonDeg) * mPerDegLon
        val by = (b.latDeg - a.latDeg) * mPerDegLat
        val segLen2 = bx * bx + by * by
        if (segLen2 <= 1e-9) return hypot(px, py)
        val t = ((px * bx + py * by) / segLen2).coerceIn(0.0, 1.0)
        return hypot(px - t * bx, py - t * by)
    }
}
