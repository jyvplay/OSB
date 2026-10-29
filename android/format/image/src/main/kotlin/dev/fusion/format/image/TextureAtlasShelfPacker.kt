// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

/**
 * Pure-JVM **Shelf-First-Fit Decreasing (`FFD`) 2D Glyph & Thumbnail Texture Atlas Bin-Packer**
 * for `:core:rendering` (packing custom vector icon rasters, Recall thumbnails, and PDF glyphs
 * without overlapping texels + mandatory 1px bilinear bleeding gutter!).
 */
object TextureAtlasShelfPacker {
    const val SCOPE =
        "2D Shelf First-Fit-Decreasing (FFD) texture atlas bin-packer with 1px bilinear gutter and zero-overlap guarantee."

    data class SpriteRequest(val id: String, val widthPx: Int, val heightPx: Int)
    data class PackedSprite(val id: String, val x: Int, val y: Int, val widthPx: Int, val heightPx: Int)

    data class AtlasPackResult(
        val atlasWidthPx: Int,
        val atlasHeightPx: Int,
        val placements: List<PackedSprite>,
        val rejectedIds: List<String>,
        val occupancyRatio: Double,
    )

    fun pack(requests: List<SpriteRequest>, atlasWidthPx: Int = 1024, atlasHeightPx: Int = 1024, gutterPx: Int = 1): AtlasPackResult {
        require(atlasWidthPx >= 64 && atlasHeightPx >= 64 && gutterPx in 0..4)
        val sorted = requests.sortedWith(compareByDescending<SpriteRequest> { it.heightPx }.thenByDescending { it.widthPx })
        data class Shelf(var cursorX: Int, val y: Int, val height: Int)
        val shelves = ArrayList<Shelf>()
        val placed = ArrayList<PackedSprite>()
        val rejected = ArrayList<String>()
        var nextShelfY = gutterPx

        for (req in sorted) {
            require(req.widthPx > 0 && req.heightPx > 0)
            val neededW = req.widthPx + gutterPx
            val neededH = req.heightPx + gutterPx
            val existing = shelves.firstOrNull { req.heightPx <= it.height && it.cursorX + neededW <= atlasWidthPx }
            if (existing != null) {
                placed.add(PackedSprite(req.id, existing.cursorX, existing.y, req.widthPx, req.heightPx))
                existing.cursorX += neededW
            } else if (neededW + gutterPx <= atlasWidthPx && nextShelfY + neededH <= atlasHeightPx) {
                val shelf = Shelf(gutterPx + neededW, nextShelfY, req.heightPx)
                shelves.add(shelf)
                placed.add(PackedSprite(req.id, gutterPx, nextShelfY, req.widthPx, req.heightPx))
                nextShelfY += neededH
            } else {
                rejected.add(req.id)
            }
        }
        val usedArea = placed.sumOf { it.widthPx.toLong() * it.heightPx.toLong() }.toDouble()
        val totalArea = atlasWidthPx.toDouble() * atlasHeightPx.toDouble()
        return AtlasPackResult(atlasWidthPx, atlasHeightPx, placed, rejected, usedArea / totalArea)
    }
}
