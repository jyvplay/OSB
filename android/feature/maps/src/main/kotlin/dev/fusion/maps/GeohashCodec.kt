// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

/**
 * R146 — clean-room geohash codec (encode / decode / edge neighbour).
 *
 * Standard base32 geohash (`0123456789bcdefghjkmnpqrstuvwxyz`): longitude is
 * bisected first (even bits), latitude second (odd bits), each cell emitting
 * 5 bits as one character. Decode recovers the bounding box; `neighbor`
 * returns the adjacent cell of the same precision by shifting the cell
 * centre by exactly one cell span and re-encoding, wrapping longitude across
 * the antimeridian and refusing to cross a pole.
 *
 * Declared narrowings: latitude in [-90, 90], longitude in [-180, 180];
 * precision 1..12 characters; neighbour latitude must stay within
 * [-90, 90] (polar edge refused) — longitude wraps into [-180, 180).
 * Bounding boxes print with 8 decimal places so an independent Python
 * implementation can be diffed byte-for-byte.
 */
object GeohashCodec {
    const val SCOPE =
        "Base32 geohash encode/decode/neighbour with polar refusal and antimeridian wrap; no tile math, no rendering."

    const val ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"
    const val MIN_PRECISION = 1
    const val MAX_PRECISION = 12

    data class Box(val south: Double, val west: Double, val north: Double, val east: Double)

    fun encode(latitude: Double, longitude: Double, precision: Int): String {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "latitude out of range" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "longitude out of range" }
        require(precision in MIN_PRECISION..MAX_PRECISION) { "precision out of range" }
        var latLo = -90.0
        var latHi = 90.0
        var lonLo = -180.0
        var lonHi = 180.0
        val out = StringBuilder(precision)
        var bit = 0
        var ch = 0
        var even = true
        while (out.length < precision) {
            if (even) {
                val mid = (lonLo + lonHi) / 2.0
                if (longitude >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    lonLo = mid
                } else {
                    lonHi = mid
                }
            } else {
                val mid = (latLo + latHi) / 2.0
                if (latitude >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    latLo = mid
                } else {
                    latHi = mid
                }
            }
            even = !even
            bit++
            if (bit == 5) {
                out.append(ALPHABET[ch])
                ch = 0
                bit = 0
            }
        }
        return out.toString()
    }

    fun decode(hash: String): Box {
        require(hash.length in MIN_PRECISION..MAX_PRECISION) { "hash length out of range" }
        var latLo = -90.0
        var latHi = 90.0
        var lonLo = -180.0
        var lonHi = 180.0
        var even = true
        for (c in hash) {
            val idx = ALPHABET.indexOf(c)
            require(idx >= 0) { "invalid geohash character: $c" }
            for (shift in 4 downTo 0) {
                val set = (idx shr shift) and 1 == 1
                if (even) {
                    val mid = (lonLo + lonHi) / 2.0
                    if (set) lonLo = mid else lonHi = mid
                } else {
                    val mid = (latLo + latHi) / 2.0
                    if (set) latLo = mid else latHi = mid
                }
                even = !even
            }
        }
        return Box(latLo, lonLo, latHi, lonHi)
    }

    /** `dir` is one of N, S, E, W. */
    fun neighbor(hash: String, dir: String): String {
        require(dir in setOf("N", "S", "E", "W")) { "bad neighbour direction" }
        val box = decode(hash)
        val latSpan = box.north - box.south
        val lonSpan = box.east - box.west
        val centerLat = (box.south + box.north) / 2.0
        val centerLon = (box.west + box.east) / 2.0
        val (newLat, newLon) = when (dir) {
            "N" -> (centerLat + latSpan) to centerLon
            "S" -> (centerLat - latSpan) to centerLon
            "E" -> centerLat to wrapLon(centerLon + lonSpan)
            else -> centerLat to wrapLon(centerLon - lonSpan)
        }
        require(newLat in -90.0..90.0) { "neighbour crosses a pole" }
        return encode(newLat, newLon, hash.length)
    }

    private fun wrapLon(lon: Double): Double {
        var v = lon
        while (v > 180.0) v -= 360.0
        while (v < -180.0) v += 360.0
        return v
    }

    fun renderBox(b: Box): String =
        String.format(
            java.util.Locale.ROOT, "%.8f,%.8f,%.8f,%.8f",
            b.south, b.west, b.north, b.east
        )
}
