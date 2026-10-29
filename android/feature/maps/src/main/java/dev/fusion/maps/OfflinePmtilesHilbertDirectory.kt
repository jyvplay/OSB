// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

/**
 * Clean-room `PMTiles v3` Single-File Offline Map Archive Hilbert-Curve `tileId(z, x, y)` Bijection
 * & Run-Length Directory Binary Search Engine.
 *
 * Implements the open BSD-3/ISC PMTiles v3 specification's exact quadtree Hilbert curve bijection
 * mapping `(z, x, y)` pyramid coordinates (`z in 0..26`) to a monotonic 64-bit `tileId` so
 * spatially adjacent map tiles cluster into contiguous HTTP/file byte ranges with zero cloud tile
 * servers.
 */
object OfflinePmtilesHilbertDirectory {
    const val SCOPE =
        "Pure-JVM PMTiles v3 Hilbert-curve (z,x,y) <-> uint64 tileId bijection and run-length directory binary-search resolver."

    data class DirectoryEntry(val tileId: Long, val offsetBytes: Long, val lengthBytes: Int, val runLength: Int)

    fun zxyToTileId(z: Int, x: Int, y: Int): Long {
        require(z in 0..26) { "zoom $z outside 0..26" }
        val maxCoord = 1L shl z
        require(x.toLong() in 0 until maxCoord && y.toLong() in 0 until maxCoord) {
            "tile ($x,$y) outside zoom $z bounds 0..${maxCoord - 1}"
        }
        if (z == 0) return 0L
        // Base offset = sum_{k=0}^{z-1} 4^k = (4^z - 1) / 3
        val base = ((1L shl (2 * z)) - 1L) / 3L
        return base + xyToHilbert(z, x.toLong(), y.toLong())
    }

    fun tileIdToZxy(tileId: Long): Triple<Int, Int, Int> {
        require(tileId >= 0L) { "negative tileId $tileId" }
        if (tileId == 0L) return Triple(0, 0, 0)
        var z = 1
        while (z <= 26) {
            val nextBase = ((1L shl (2 * (z + 1))) - 1L) / 3L
            if (tileId < nextBase) break
            z++
        }
        require(z <= 26) { "tileId $tileId exceeds zoom 26" }
        val base = ((1L shl (2 * z)) - 1L) / 3L
        val (x, y) = hilbertToXy(z, tileId - base)
        return Triple(z, x.toInt(), y.toInt())
    }

    fun findTileByteRange(sortedDir: List<DirectoryEntry>, tileId: Long): Pair<Long, Int>? {
        var lo = 0
        var hi = sortedDir.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val e = sortedDir[mid]
            if (tileId < e.tileId) {
                hi = mid - 1
            } else if (e.runLength > 0 && tileId < e.tileId + e.runLength) {
                return e.offsetBytes to e.lengthBytes
            } else {
                lo = mid + 1
            }
        }
        return null
    }

    private fun xyToHilbert(z: Int, xIn: Long, yIn: Long): Long {
        var x = xIn; var y = yIn; var d = 0L
        var s = 1L shl (z - 1)
        while (s > 0L) {
            val rx = if ((x and s) != 0L) 1L else 0L
            val ry = if ((y and s) != 0L) 1L else 0L
            d += s * s * ((3L * rx) xor ry)
            val rot = rotateQuadrant(s, x, y, rx, ry)
            x = rot.first; y = rot.second
            s = s ushr 1
        }
        return d
    }

    private fun hilbertToXy(z: Int, dist: Long): Pair<Long, Long> {
        var t = dist; var x = 0L; var y = 0L; var s = 1L
        val n = 1L shl z
        while (s < n) {
            val rx = 1L and (t / 2L)
            val ry = 1L and (t xor rx)
            val rot = rotateQuadrant(s, x, y, rx, ry)
            x = rot.first + s * rx
            y = rot.second + s * ry
            t /= 4L
            s = s shl 1
        }
        return x to y
    }

    private fun rotateQuadrant(n: Long, xIn: Long, yIn: Long, rx: Long, ry: Long): Pair<Long, Long> {
        var x = xIn; var y = yIn
        if (ry == 0L) {
            if (rx == 1L) { x = n - 1L - x; y = n - 1L - y }
            val tmp = x; x = y; y = tmp
        }
        return x to y
    }
}
