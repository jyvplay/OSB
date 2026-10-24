package dev.fusion.capture

/**
 * Rule-of-thirds overlay geometry for the capture surface: the two
 * vertical and two horizontal gridlines at 1/3 and 2/3 (integer
 * positions via the same floor-boundary law the windowing kernel
 * uses, so lines land identically across surfaces), the four power
 * points, and nearest-power-point snapping for focus hints.
 *
 * Declared narrowings: geometry only — no image analysis, no
 * composition scoring (saliency is a model, not arithmetic); zero or
 * negative dimensions refuse.
 */
object Rule3rds {
    const val SCOPE = "Thirds gridlines + power points + nearest-point snap; no image analysis or scoring."

    data class Grid(val vx1: Int, val vx2: Int, val hy1: Int, val hy2: Int)

    fun grid(width: Int, height: Int): Grid {
        require(width > 0 && height > 0) { "degenerate frame ${width}x$height" }
        return Grid(width / 3, width * 2 / 3, height / 3, height * 2 / 3)
    }

    fun powerPoints(width: Int, height: Int): List<Pair<Int, Int>> {
        val g = grid(width, height)
        return listOf(g.vx1 to g.hy1, g.vx2 to g.hy1, g.vx1 to g.hy2, g.vx2 to g.hy2)
    }

    /** Nearest power point by squared distance; ties resolve to the earliest in [powerPoints] order. */
    fun nearestPowerPoint(width: Int, height: Int, x: Int, y: Int): Pair<Int, Int> {
        require(x in 0 until width && y in 0 until height) { "point ($x,$y) outside the frame" }
        return powerPoints(width, height).minByOrNull { (px, py) ->
            val dx = (px - x).toLong(); val dy = (py - y).toLong()
            dx * dx + dy * dy
        }!!
    }
}
