// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import java.util.Collections
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bounded GPX 1.1 track import for the offline atlas. Reads trkpt latitude/longitude (and
 * optional ele) with a small strict scanner — no external XML dependency, no network fetch,
 * no route planning, no navigation. Track length is a spherical sum (WGS84 mean radius),
 * not a surveyed or road distance.
 */
object GpxTrack {
    const val MAX_BYTES = 512 * 1024
    const val MAX_POINTS = 8192
    const val MEAN_RADIUS_METERS = 6_371_008.8

    data class Point(val latitude: Double, val longitude: Double, val elevationMeters: Double?) {
        init {
            require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
            require(elevationMeters == null || (elevationMeters.isFinite() && elevationMeters in -12000.0..12000.0))
        }
    }
    data class Track(val name: String?, val points: List<Point>, val lengthMeters: Double)

    private fun attr(tag: String, name: String): String? {
        val key = "$name=\""
        val start = tag.indexOf(key)
        if (start < 0) return null
        val end = tag.indexOf('"', start + key.length)
        if (end < 0) return null
        return tag.substring(start + key.length, end)
    }

    fun parse(text: String): Track {
        require(text.length <= MAX_BYTES) { "GPX exceeds $MAX_BYTES bytes" }
        require("<gpx" in text) { "Not a GPX document" }
        val name = Regex("<name>([^<>]{1,128})</name>").find(text)?.groupValues?.get(1)?.trim()
        val points = mutableListOf<Point>()
        var cursor = 0
        while (true) {
            val open = text.indexOf("<trkpt", cursor)
            if (open < 0) break
            val tagEnd = text.indexOf('>', open)
            require(tagEnd > open && tagEnd - open <= 512) { "Bad trkpt tag" }
            val tag = text.substring(open, tagEnd + 1)
            val lat = attr(tag, "lat")?.toDoubleOrNull()
            val lon = attr(tag, "lon")?.toDoubleOrNull()
            require(lat != null && lon != null) { "trkpt needs numeric lat/lon" }
            var ele: Double? = null
            if (!tag.endsWith("/>")) {
                val close = text.indexOf("</trkpt>", tagEnd)
                require(close > tagEnd && close - tagEnd <= 1024) { "Bad trkpt body" }
                val body = text.substring(tagEnd + 1, close)
                Regex("<ele>(-?[0-9]+(?:\\.[0-9]+)?)</ele>").find(body)?.let {
                    ele = it.groupValues[1].toDouble()
                }
                cursor = close + 8
            } else cursor = tagEnd + 1
            require(points.size < MAX_POINTS) { "More than $MAX_POINTS track points" }
            points += Point(lat, lon, ele)
        }
        require(points.size >= 2) { "A track needs at least two points" }
        var length = 0.0
        for (i in 1 until points.size) length += haversine(points[i - 1], points[i])
        return Track(name, Collections.unmodifiableList(points), length)
    }

    fun haversine(a: Point, b: Point): Double {
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude); val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * MEAN_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
