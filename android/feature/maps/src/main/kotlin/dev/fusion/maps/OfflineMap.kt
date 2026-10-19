// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Latitude must be −90…90 and longitude −180…180" } }
}
object EarthGeometry {
    const val MAX_MERCATOR_LATITUDE = 85.0511287798066
    const val MEAN_RADIUS_METERS = 6_371_008.8
    data class WorldPoint(val x: Double, val y: Double)
    fun project(point: GeoPoint): WorldPoint {
        val phi = Math.toRadians(point.latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
        return WorldPoint((point.longitude + 180) / 360, ((1 - ln(tan(Math.PI / 4 + phi / 2)) / Math.PI) / 2).coerceIn(0.0, 1.0))
    }
    fun unproject(x: Double, y: Double): GeoPoint {
        require(x.isFinite() && y.isFinite() && abs(x) < 1e12)
        val wrapped = x - floor(x)
        return GeoPoint(Math.toDegrees(atan(sinh(Math.PI * (1 - 2 * y.coerceIn(0.0, 1.0))))), wrapped * 360 - 180)
    }
    /** Spherical great-circle distance, not an ellipsoidal survey or a road route. */
    fun meters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dl = Math.toRadians(b.latitude - a.latitude); val dn = Math.toRadians(b.longitude - a.longitude)
        val h = (sin(dl / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dn / 2).pow(2)).coerceIn(0.0, 1.0)
        return 2 * MEAN_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
    }
}
data class MapCamera(val center: GeoPoint = GeoPoint(15.0, 0.0), val zoom: Int = 1) {
    init { require(zoom in 0..18) }
    val worldSize get() = 256.0 * (1 shl zoom)
    fun screen(point: GeoPoint, width: Double, height: Double): EarthGeometry.WorldPoint {
        viewport(width, height)
        val p = EarthGeometry.project(point); val c = EarthGeometry.project(center)
        var dx = p.x - c.x
        dx -= floor(dx + .5) // nearest world copy, across the dateline
        return EarthGeometry.WorldPoint(width / 2 + dx * worldSize, height / 2 + (p.y - c.y) * worldSize)
    }
    fun at(x: Double, y: Double, width: Double, height: Double): GeoPoint {
        viewport(width, height); require(x.isFinite() && y.isFinite() && abs(x) < 1e8 && abs(y) < 1e8)
        val c = EarthGeometry.project(center)
        return EarthGeometry.unproject(c.x + (x - width / 2) / worldSize, c.y + (y - height / 2) / worldSize)
    }
    fun pan(dx: Double, dy: Double): MapCamera {
        require(dx.isFinite() && dy.isFinite() && abs(dx) < 1e8 && abs(dy) < 1e8)
        val c = EarthGeometry.project(center)
        return copy(center = EarthGeometry.unproject(c.x - dx / worldSize, c.y - dy / worldSize))
    }
    private fun viewport(w: Double, h: Double) { require(w.isFinite() && h.isFinite() && w in 1.0..32768.0 && h in 1.0..32768.0) }
}
data class MapPin(val id: Long, val name: String, val point: GeoPoint) {
    init { require(id > 0 && name.length in 1..64 && name == name.trim() && name.none { it.isISOControl() || it.isSurrogate() }) }
}
class MapBookmarks(val revision: Long = 0, val nextId: Long = 1, pins: List<MapPin> = emptyList()) {
    val pins: List<MapPin> = Collections.unmodifiableList(ArrayList(pins))
    init { require(revision in 0 until Long.MAX_VALUE && nextId in 1 until Long.MAX_VALUE && pins.size <= 256 && pins.map { it.id }.distinct().size == pins.size && pins.all { it.id < nextId }) }
    fun add(name: String, point: GeoPoint): MapBookmarks {
        require(pins.size < 256 && nextId < Long.MAX_VALUE - 1 && revision < Long.MAX_VALUE - 1)
        return MapBookmarks(revision + 1, nextId + 1, pins + MapPin(nextId, name.trim(), point))
    }
    fun remove(id: Long): MapBookmarks {
        require(pins.any { it.id == id } && revision < Long.MAX_VALUE - 1)
        return MapBookmarks(revision + 1, nextId, pins.filterNot { it.id == id })
    }
    override fun equals(other: Any?) = other is MapBookmarks && revision == other.revision && nextId == other.nextId && pins == other.pins
    override fun hashCode() = listOf(revision, nextId, pins).hashCode()
}
object MapBookmarkCodec {
    const val MAX_BYTES = 64 * 1024
    fun encode(book: MapBookmarks): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x464d5031); out.writeLong(book.revision); out.writeLong(book.nextId); out.writeInt(book.pins.size)
            book.pins.forEach { pin ->
                out.writeLong(pin.id); val name = pin.name.toByteArray(Charsets.UTF_8); out.writeInt(name.size); out.write(name)
                out.writeDouble(pin.point.latitude); out.writeDouble(pin.point.longitude)
            }
        }
        return bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }
    fun decode(bytes: ByteArray): MapBookmarks {
        require(bytes.size in 24..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == 0x464d5031) { "Unknown map bookmark format" }
            val revision = input.readLong(); val nextId = input.readLong(); val count = input.readInt(); require(count in 0..256)
            val pins = List(count) {
                val id = input.readLong(); val size = input.readInt(); require(size in 1..192)
                val raw = ByteArray(size); input.readFully(raw)
                val name = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
                MapPin(id, name, GeoPoint(input.readDouble(), input.readDouble()))
            }
            require(input.available() == 0); return MapBookmarks(revision, nextId, pins)
        }
    }
}

/** Shipped Natural Earth outline asset only, not a remote/unbounded GeoJSON importer. */
object LandOutlines {
    const val MAX_BYTES = 256 * 1024
    fun read(input: InputStream): List<List<GeoPoint>> {
        val bytes = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; require(bytes.size() + n <= MAX_BYTES); bytes.write(buffer, 0, n) }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        val lines = text.lineSequence().iterator(); require(lines.hasNext() && lines.next() == "FUSION-LAND-1")
        val rings = mutableListOf<List<GeoPoint>>(); var current = mutableListOf<GeoPoint>(); var points = 0
        while (lines.hasNext()) {
            val line = lines.next()
            if (line == ">") {
                if (current.isNotEmpty()) { require(current.size >= 4 && current.first() == current.last()); rings.add(current.toList()) }
                require(rings.size < 512); current = mutableListOf()
            } else if (line.isNotEmpty()) {
                require(line.length <= 64 && ++points <= 12000)
                val fields = line.split(','); require(fields.size == 2)
                current.add(GeoPoint(fields[1].toDouble(), fields[0].toDouble()))
            }
        }
        if (current.isNotEmpty()) { require(current.size >= 4 && current.first() == current.last()); rings.add(current.toList()) }
        require(rings.isNotEmpty())
        return Collections.unmodifiableList(rings.map { Collections.unmodifiableList(it) })
    }
}
