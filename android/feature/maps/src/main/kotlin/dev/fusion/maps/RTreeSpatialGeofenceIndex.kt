package dev.fusion.maps

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clean-room Offline Spatial Bounding-Box `R-Tree` / STR-Packed Index, Haversine Geodesic Distance,
 * and Ray-Casting Point-in-Polygon Geofence Evaluator (Apache-2.0; zero network tiles or telemetry!).
 */
object RTreeSpatialGeofenceIndex {
    const val SCOPE =
        "STR-packed 2D spatial R-Tree + Haversine great-circle kNN + Jordan ray-casting polygon geofence engine"

    private const val EARTH_RADIUS_M = 6_371_008.8

    data class GeoPoint(val latDeg: Double, val lonDeg: Double)
    data class PoiEntry(val id: String, val name: String, val location: GeoPoint)

    class SpatialIndex(entries: List<PoiEntry>, nodeCapacity: Int = 8) {
        private data class Bucket(
            val minLat: Double,
            val maxLat: Double,
            val minLon: Double,
            val maxLon: Double,
            val items: List<PoiEntry>,
        )

        private val buckets: List<Bucket>

        init {
            val sortedLat = entries.sortedBy { it.location.latDeg }
            val sliceSize = max(nodeCapacity * 4, 1)
            val bList = ArrayList<Bucket>()
            for (latSlice in sortedLat.chunked(sliceSize)) {
                for (leaf in latSlice.sortedBy { it.location.lonDeg }.chunked(nodeCapacity.coerceAtLeast(2))) {
                    bList.add(
                        Bucket(
                            minLat = leaf.minOf { it.location.latDeg },
                            maxLat = leaf.maxOf { it.location.latDeg },
                            minLon = leaf.minOf { it.location.lonDeg },
                            maxLon = leaf.maxOf { it.location.lonDeg },
                            items = leaf,
                        ),
                    )
                }
            }
            buckets = bList
        }

        fun queryBoundingBox(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<PoiEntry> {
            val hits = ArrayList<PoiEntry>()
            for (b in buckets) {
                if (b.maxLat < minLat || b.minLat > maxLat || b.maxLon < minLon || b.minLon > maxLon) continue
                for (p in b.items) {
                    if (p.location.latDeg in minLat..maxLat && p.location.lonDeg in minLon..maxLon) hits.add(p)
                }
            }
            return hits.sortedBy { it.id }
        }

        fun nearestWithinMeters(origin: GeoPoint, radiusMeters: Double, k: Int = 5): List<Pair<PoiEntry, Double>> {
            val dLat = (radiusMeters / EARTH_RADIUS_M) * (180.0 / Math.PI)
            val cosLat = cos(Math.toRadians(origin.latDeg)).coerceAtLeast(0.01)
            val dLon = dLat / cosLat
            return queryBoundingBox(origin.latDeg - dLat, origin.lonDeg - dLon, origin.latDeg + dLat, origin.lonDeg + dLon)
                .map { it to haversineMeters(origin, it.location) }
                .filter { it.second <= radiusMeters }
                .sortedWith(compareBy<Pair<PoiEntry, Double>> { it.second }.thenBy { it.first.id })
                .take(k)
        }
    }

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latDeg - a.latDeg)
        val dLon = Math.toRadians(b.lonDeg - a.lonDeg)
        val lat1 = Math.toRadians(a.latDeg)
        val lat2 = Math.toRadians(b.latDeg)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** Jordan curve theorem even-odd horizontal ray-casting point-in-polygon check. */
    fun isPointInsidePolygon(point: GeoPoint, ringVertices: List<GeoPoint>): Boolean {
        require(ringVertices.size >= 3) { "Polygon geofence requires >= 3 vertices" }
        var inside = false
        var j = ringVertices.lastIndex
        for (i in ringVertices.indices) {
            val yi = ringVertices[i].latDeg; val xi = ringVertices[i].lonDeg
            val yj = ringVertices[j].latDeg; val xj = ringVertices[j].lonDeg
            val intersects = ((yi > point.latDeg) != (yj > point.latDeg)) &&
                (point.lonDeg < (xj - xi) * (point.latDeg - yi) / (yj - yi) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}
