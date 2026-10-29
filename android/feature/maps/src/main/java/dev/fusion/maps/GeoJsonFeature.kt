// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clean-room RFC 7946 GeoJSON (`Point`, `LineString`, `Polygon`, `FeatureCollection`) parser,
 * WGS84 coordinate validator, bounding-box computer & Haversine length calculator (`GeoJsonFeature`).
 *
 * Powers `:feature:maps` offline GPX/GeoJSON trail & pin overlays without Play Services Maps SDK!
 */
object GeoJsonFeature {
    const val SCOPE =
        "RFC 7946 GeoJSON parser + WGS84 [-180..180, -90..90] validator + bbox + Haversine polyline length + ray-cast point-in-polygon; MultiPolygon holes out of scope."

    const val EARTH_RADIUS_M = 6_371_008.8

    data class LonLat(val lon: Double, val lat: Double) {
        init {
            require(lon.isFinite() && lon in -180.0..180.0) { "Longitude out of RFC 7946 WGS84 bounds: $lon" }
            require(lat.isFinite() && lat in -90.0..90.0) { "Latitude out of RFC 7946 WGS84 bounds: $lat" }
        }
    }

    data class BBox(val west: Double, val south: Double, val east: Double, val north: Double)

    sealed interface Geometry {
        fun allPoints(): List<LonLat>
        data class Point(val coord: LonLat) : Geometry {
            override fun allPoints(): List<LonLat> = listOf(coord)
        }
        data class LineString(val coords: List<LonLat>) : Geometry {
            init { require(coords.size >= 2) { "GeoJSON LineString requires >= 2 positions" } }
            override fun allPoints(): List<LonLat> = coords
        }
        data class Polygon(val outerRing: List<LonLat>) : Geometry {
            init {
                require(outerRing.size >= 4) { "RFC 7946 Polygon linear ring requires >= 4 positions" }
                require(outerRing.first() == outerRing.last()) { "RFC 7946 Polygon linear ring must be closed" }
            }
            override fun allPoints(): List<LonLat> = outerRing
        }
    }

    data class Feature(val id: String?, val geometry: Geometry, val properties: Map<String, String>)

    fun computeBBox(features: List<Feature>): BBox {
        val pts = features.flatMap { it.geometry.allPoints() }
        require(pts.isNotEmpty()) { "Cannot compute BBox of empty feature list" }
        var w = Double.POSITIVE_INFINITY
        var s = Double.POSITIVE_INFINITY
        var e = Double.NEGATIVE_INFINITY
        var n = Double.NEGATIVE_INFINITY
        for (p in pts) {
            w = min(w, p.lon); s = min(s, p.lat)
            e = max(e, p.lon); n = max(n, p.lat)
        }
        return BBox(w, s, e, n)
    }

    fun haversineMeters(a: LonLat, b: LonLat): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2.0).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2.0).pow(2)
        return 2.0 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    fun lineLengthMeters(line: Geometry.LineString): Double =
        line.coords.zipWithNext { a, b -> haversineMeters(a, b) }.sum()

    fun containsPoint(poly: Geometry.Polygon, pt: LonLat): Boolean {
        val ring = poly.outerRing
        var inside = false
        var j = ring.size - 2
        for (i in 0 until ring.size - 1) {
            val xi = ring[i].lon; val yi = ring[i].lat
            val xj = ring[j].lon; val yj = ring[j].lat
            val intersect = ((yi > pt.lat) != (yj > pt.lat)) &&
                (pt.lon < (xj - xi) * (pt.lat - yi) / ((yj - yi).let { if (it == 0.0) 1e-15 else it }) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun parseFeatureCollection(json: String): List<Feature> {
        val root = MiniJson.parse(json) as? Map<*, *> ?: error("GeoJSON root must be an object")
        return when (root["type"]) {
            "FeatureCollection" -> {
                val arr = root["features"] as? List<*> ?: error("Missing features array")
                arr.map { parseFeatureObj(it as? Map<*, *> ?: error("Feature must be object")) }
            }
            "Feature" -> listOf(parseFeatureObj(root))
            else -> listOf(Feature(null, parseGeometryObj(root), emptyMap()))
        }
    }

    private fun parseFeatureObj(obj: Map<*, *>): Feature {
        require(obj["type"] == "Feature") { "Expected type=Feature" }
        val geomObj = obj["geometry"] as? Map<*, *> ?: error("Feature missing geometry")
        val propsObj = obj["properties"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val props = propsObj.entries.associate { (k, v) -> k.toString() to (v?.toString() ?: "") }
        return Feature(obj["id"]?.toString(), parseGeometryObj(geomObj), props)
    }

    private fun parseGeometryObj(obj: Map<*, *>): Geometry {
        val coords = obj["coordinates"] ?: error("Geometry missing coordinates")
        return when (val t = obj["type"]) {
            "Point" -> Geometry.Point(toLonLat(coords as List<*>))
            "LineString" -> Geometry.LineString((coords as List<*>).map { toLonLat(it as List<*>) })
            "Polygon" -> {
                val rings = coords as List<*>
                require(rings.isNotEmpty()) { "Polygon requires outer ring" }
                Geometry.Polygon((rings.first() as List<*>).map { toLonLat(it as List<*>) })
            }
            else -> throw IllegalArgumentException("Unsupported GeoJSON geometry type: $t")
        }
    }

    private fun toLonLat(pair: List<*>): LonLat {
        require(pair.size >= 2) { "GeoJSON position requires [lon, lat]" }
        return LonLat((pair[0] as Number).toDouble(), (pair[1] as Number).toDouble())
    }

    private object MiniJson {
        fun parse(s: String): Any? = Reader(s).let { r ->
            val v = r.readVal()
            r.ws()
            require(r.i == s.length) { "Trailing GeoJSON text" }
            v
        }

        private class Reader(val s: String) {
            var i = 0
            fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
            fun readVal(): Any? {
                ws()
                return when (s[i]) {
                    '{' -> {
                        i++; ws()
                        val m = LinkedHashMap<String, Any?>()
                        if (s[i] == '}') { i++; m } else {
                            while (true) {
                                ws(); val k = readStr(); ws(); require(s[i++] == ':')
                                m[k] = readVal(); ws()
                                if (s[i] == '}') { i++; break }
                                require(s[i++] == ',')
                            }
                            m
                        }
                    }
                    '[' -> {
                        i++; ws()
                        val a = ArrayList<Any?>()
                        if (s[i] == ']') { i++; a } else {
                            while (true) {
                                a.add(readVal()); ws()
                                if (s[i] == ']') { i++; break }
                                require(s[i++] == ',')
                            }
                            a
                        }
                    }
                    '"' -> readStr()
                    't' -> { i += 4; true }
                    'f' -> { i += 5; false }
                    'n' -> { i += 4; null }
                    else -> {
                        val st = i
                        while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
                        s.substring(st, i).toDouble()
                    }
                }
            }
            fun readStr(): String {
                require(s[i++] == '"')
                val sb = StringBuilder()
                while (i < s.length) {
                    val c = s[i++]
                    if (c == '"') return sb.toString()
                    sb.append(if (c == '\\') s[i++] else c)
                }
                error("Unterminated string")
            }
        }
    }
}
