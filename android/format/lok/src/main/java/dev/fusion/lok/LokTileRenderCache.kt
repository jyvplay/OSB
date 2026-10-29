// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Clean-room LibreOfficeKit-style (`FF-01`) 256x256 document tile pyramid coordinate planner,
 * twips (`1/1440 inch`) <-> device pixel converter, dirty-rectangle tile invalidator, and
 * byte-bounded LRU tile cache.
 */
class LokTileRenderCache(
    val tileSizePx: Int = 256,
    val maxCachedBytes: Long = 16L * 1024L * 1024L,
) {
    init {
        require(tileSizePx in setOf(128, 256, 512)) { "tileSizePx must be 128, 256, or 512" }
        require(maxCachedBytes >= tileSizePx.toLong() * tileSizePx * 4L) { "cache budget < 1 RGBA tile" }
    }

    data class TileKey(
        val partIndex: Int,
        val zoomPercent: Int,
        val tileCol: Int,
        val tileRow: Int,
    ) {
        init {
            require(partIndex >= 0 && zoomPercent in 25..400 && tileCol >= 0 && tileRow >= 0) {
                "invalid TileKey($partIndex, $zoomPercent%, $tileCol, $tileRow)"
            }
        }
    }

    data class TwipsRect(val left: Long, val top: Long, val width: Long, val height: Long) {
        init {
            require(left >= 0 && top >= 0 && width > 0 && height > 0) { "invalid TwipsRect" }
        }
        val right: Long get() = left + width
        val bottom: Long get() = top + height

        fun intersects(other: TwipsRect): Boolean =
            left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    private val entries = LinkedHashMap<TileKey, ByteArray>(64, 0.75f, true)
    var currentBytes: Long = 0L
        private set

    fun size(): Int = entries.size

    fun twipsPerTile(zoomPercent: Int, dpi: Int = 96): Long {
        require(zoomPercent in 25..400 && dpi in 72..600) { "invalid zoom/dpi" }
        // 1 inch = 1440 twips; at 100% zoom, 1 px = (1440 / dpi) twips
        return ((tileSizePx.toLong() * 1440L * 100L) / (dpi.toLong() * zoomPercent.toLong())).coerceAtLeast(1L)
    }

    fun tileBoundsTwips(key: TileKey, dpi: Int = 96): TwipsRect {
        val span = twipsPerTile(key.zoomPercent, dpi)
        return TwipsRect(
            left = key.tileCol * span,
            top = key.tileRow * span,
            width = span,
            height = span,
        )
    }

    fun visibleTiles(
        partIndex: Int,
        zoomPercent: Int,
        viewportTwips: TwipsRect,
        documentWidthTwips: Long,
        documentHeightTwips: Long,
        dpi: Int = 96,
    ): List<TileKey> {
        val span = twipsPerTile(zoomPercent, dpi)
        val clampedRight = minOf(viewportTwips.right, documentWidthTwips).coerceAtLeast(1L)
        val clampedBottom = minOf(viewportTwips.bottom, documentHeightTwips).coerceAtLeast(1L)
        val c0 = (viewportTwips.left / span).toInt().coerceAtLeast(0)
        val c1 = ((clampedRight - 1L) / span).toInt().coerceAtLeast(c0)
        val r0 = (viewportTwips.top / span).toInt().coerceAtLeast(0)
        val r1 = ((clampedBottom - 1L) / span).toInt().coerceAtLeast(r0)
        val out = ArrayList<TileKey>((c1 - c0 + 1) * (r1 - r0 + 1))
        for (r in r0..r1) {
            for (c in c0..c1) out.add(TileKey(partIndex, zoomPercent, c, r))
        }
        return out
    }

    fun put(key: TileKey, rgbaBytes: ByteArray) {
        val expected = tileSizePx * tileSizePx * 4
        require(rgbaBytes.size == expected) { "RGBA tile must be $expected bytes, got ${rgbaBytes.size}" }
        entries.remove(key)?.let { currentBytes -= it.size }
        while (currentBytes + rgbaBytes.size > maxCachedBytes && entries.isNotEmpty()) {
            val eldest = entries.entries.iterator().next()
            currentBytes -= eldest.value.size
            entries.remove(eldest.key)
        }
        entries[key] = rgbaBytes.copyOf()
        currentBytes += rgbaBytes.size
    }

    fun get(key: TileKey): ByteArray? = entries[key]?.copyOf()

    fun invalidateDirtyRect(partIndex: Int, dirtyTwips: TwipsRect, dpi: Int = 96): Int {
        val victims = entries.keys.filter { k ->
            k.partIndex == partIndex && tileBoundsTwips(k, dpi).intersects(dirtyTwips)
        }
        for (k in victims) {
            entries.remove(k)?.let { currentBytes -= it.size }
        }
        return victims.size
    }
}
