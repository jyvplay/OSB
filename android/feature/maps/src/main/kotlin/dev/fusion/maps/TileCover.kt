// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

/**
 * Bounding-box tile coverage over [TileMath].
 *
 * Sits next to [TileMath] (single point <-> tile): nothing enumerates the
 * tile SET covering a download bbox at a zoom — the loop an offline-pack
 * planner actually needs. Corners reuse [TileMath.tileOf], so the
 * projection, zoom bounds, and Mercator latitude clamp are inherited, not
 * reimplemented; this kernel adds only bound validation, x/y range
 * expansion in row-major order, and the tile-count cap. Longitude must not
 * wrap (min <= max within -180..180 — antimeridian boxes are refused, not
 * silently half-covered), and coverage over the cap is refused rather than
 * truncated, because a truncated pack would download a wrong-shaped region.
 * At most [MAX_TILES] tiles; zoom 19 planet-scale boxes refuse loudly.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object TileCover {
    const val MAX_TILES = 4096

    /**
     * Lists every tile covering the lon/lat box at zoom [z], row-major
     * (north to south, west to east).
     *
     * @throws IllegalArgumentException on bad bounds, wrapped longitude,
     * or coverage over the tile cap.
     */
    fun cover(lonMin: Double, latMin: Double, lonMax: Double, latMax: Double, z: Int): List<TileMath.Tile> {
        TileMath.checkZoom(z)
        require(lonMin.isFinite() && latMin.isFinite() && lonMax.isFinite() && latMax.isFinite()) {
            "bounds must be finite"
        }
        require(lonMin in -180.0..180.0 && lonMax in -180.0..180.0) { "lon must be within -180..180" }
        require(lonMin <= lonMax) { "lon wraps or inverts" }
        require(latMin <= latMax) { "lat inverts" }
        val sw = TileMath.tileOf(lonMin, latMin, z)
        val ne = TileMath.tileOf(lonMax, latMax, z)
        val x0 = minOf(sw.x, ne.x)
        val x1 = maxOf(sw.x, ne.x)
        val y0 = minOf(sw.y, ne.y)
        val y1 = maxOf(sw.y, ne.y)
        val count = (x1 - x0 + 1L) * (y1 - y0 + 1L)
        require(count <= MAX_TILES) { "coverage $count tiles over cap" }
        val out = ArrayList<TileMath.Tile>(count.toInt())
        for (y in y0..y1) for (x in x0..x1) out.add(TileMath.Tile(x, y, z))
        return out
    }
}
