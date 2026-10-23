// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

/**
 * Google encoded-polyline decode/encode (1e5 precision).
 *
 * Sits next to [TileMath] (tiles), [TileCover] (coverage), [ScaleBar]
 * (ground resolution), and [GpxTrack] (which owns haversine distance):
 * nothing reads the compressed path strings directions APIs return.
 * [decode] expands the varint/delta/zigzag encoding to lat/lng pairs;
 * [encode] inverts it, so `decode(encode(p)) == p` is the round-trip
 * contract. Precision is fixed at 1e-5 degrees (~1.1 m); coordinates
 * outside lat ±90 / lng ±180 are refused, never wrapped.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object PolyLine {
    const val MAX_POINTS = 100_000

    /** A decoded path point in degrees. */
    data class Pt(val lat: Double, val lng: Double)

    /**
     * Decodes an encoded polyline to points.
     *
     * @throws IllegalArgumentException on bad characters, a truncated
     * sequence, or an over-cap path.
     */
    fun decode(code: String): List<Pt> {
        require(code.length <= MAX_POINTS * 12) { "code over cap" }
        for (c in code) require(c in '?'..'~') { "bad character" }
        val out = ArrayList<Pt>()
        var i = 0
        var lat = 0
        var lng = 0
        fun next(): Int {
            var shift = 0
            var result = 0
            while (true) {
                require(i < code.length) { "truncated" }
                val b = code[i++].code - 63
                result = result or ((b and 0x1F) shl shift)
                shift += 5
                require(shift <= 35) { "value over cap" }
                if (b < 0x20) break
            }
            return if (result and 1 != 0) (result shr 1).inv() else result shr 1
        }
        while (i < code.length) {
            lat += next()
            lng += next()
            require(out.size < MAX_POINTS) { "too many points" }
            out.add(Pt(lat / 1e5, lng / 1e5))
        }
        return out
    }

    /**
     * Encodes [points] to a polyline string.
     *
     * @throws IllegalArgumentException on out-of-range coordinates or an
     * over-cap path.
     */
    fun encode(points: List<Pt>): String {
        require(points.size <= MAX_POINTS) { "too many points" }
        val sb = StringBuilder()
        var plat = 0
        var plng = 0
        fun emit(v: Int) {
            var u = if (v < 0) (v shl 1).inv() else v shl 1
            while (u >= 0x20) {
                sb.append((((u and 0x1F) or 0x20) + 63).toChar())
                u = u shr 5
            }
            sb.append((u + 63).toChar())
        }
        for (p in points) {
            require(p.lat.isFinite() && p.lng.isFinite()) { "non-finite" }
            require(p.lat in -90.0..90.0 && p.lng in -180.0..180.0) { "out of range" }
            val la = kotlin.math.round(p.lat * 1e5).toInt()
            val ln = kotlin.math.round(p.lng * 1e5).toInt()
            emit(la - plat)
            emit(ln - plng)
            plat = la
            plng = ln
        }
        return sb.toString()
    }
}
