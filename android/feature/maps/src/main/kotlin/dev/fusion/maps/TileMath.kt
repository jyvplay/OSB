// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sinh
import kotlin.math.tan

/**
 * R28 EXECUTED host kernel. Slippy-map tile math (the OSM/Slippy convention):
 * lon/lat <-> tile x/y at zoom 0..19, tile bounds, and quadkeys. Pure integer
 * + double math; no network, no tile fetching, no rendering.
 */
object TileMath {
    const val MAX_ZOOM = 19
    const val MAX_LAT = 85.05112878
    private val QUADKEY = Regex("[0-3]{0,19}")

    data class Tile(val x: Int, val y: Int, val z: Int) {
        init {
            require(z in 0..MAX_ZOOM) { "zoom must be 0..$MAX_ZOOM" }
            val n = 1 shl z
            require(x in 0 until n && y in 0 until n) { "tile x/y out of range for zoom" }
        }
    }

    data class Bounds(val lonMin: Double, val latMin: Double, val lonMax: Double, val latMax: Double)

    fun checkZoom(z: Int) = require(z in 0..MAX_ZOOM) { "zoom must be 0..$MAX_ZOOM" }

    /** Tile containing (lon, lat). Longitude must be in range; latitude clamps to Mercator limits. */
    fun tileOf(lon: Double, lat: Double, z: Int): Tile {
        checkZoom(z)
        require(lon.isFinite() && lat.isFinite()) { "lon/lat must be finite" }
        require(lon in -180.0..180.0) { "lon must be within -180..180" }
        val n = 1 shl z
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val rad = clamped * PI / 180.0
        val y = floor((1.0 - asinh(tan(rad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return Tile(x, y, z)
    }

    private fun tileLon(x: Int, z: Int): Double = x.toDouble() / (1 shl z) * 360.0 - 180.0
    private fun tileLat(y: Int, z: Int): Double {
        val n = PI * (1.0 - 2.0 * y.toDouble() / (1 shl z))
        return atan(sinh(n)) * 180.0 / PI
    }

    /** Geographic bounds of [tile]. */
    fun boundsOf(tile: Tile): Bounds =
        Bounds(tileLon(tile.x, tile.z), tileLat(tile.y + 1, tile.z), tileLon(tile.x + 1, tile.z), tileLat(tile.y, tile.z))

    /** Quadkey for [tile] (empty string at zoom 0). */
    fun quadkeyOf(tile: Tile): String {
        val sb = StringBuilder()
        for (i in tile.z downTo 1) {
            var digit = 0
            if (tile.x and (1 shl (i - 1)) != 0) digit += 1
            if (tile.y and (1 shl (i - 1)) != 0) digit += 2
            sb.append('0' + digit)
        }
        return sb.toString()
    }

    /** Inverse of [quadkeyOf]. */
    fun tileOfQuadkey(quadkey: String): Tile {
        require(QUADKEY.matches(quadkey)) { "quadkey must be 0..19 chars of 0..3" }
        var x = 0
        var y = 0
        quadkey.forEachIndexed { index, c ->
            val mask = 1 shl (quadkey.length - 1 - index)
            when (c) {
                '1' -> x = x or mask
                '2' -> y = y or mask
                '3' -> { x = x or mask; y = y or mask }
            }
        }
        return Tile(x, y, quadkey.length)
    }
}
