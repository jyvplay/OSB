package dev.fusion.format.image

/**
 * EXIF Orientation tag semantics (values 1..8 per the EXIF spec):
 * each value maps to a rotation in {0,90,180,270} plus an optional
 * mirror; the composition laws (apply twice where self-inverse,
 * rotation adds mod 360) are executed, and the DISPLAYED dimensions
 * swap exactly for the four transposed values 5..8.
 *
 * Declared narrowings: the mapping table and dimension math only —
 * pixel transformation is ImageOps' surface; value 0 and 9+ refuse
 * (corrupt writers emit them; guessing flips photos).
 */
object ExifOrient {
    const val SCOPE = "Orientation 1..8 mapping + dimension swap; pixel work is ImageOps'; 0/9+ refuse."

    data class Op(val rotateCw: Int, val mirrorHorizontal: Boolean)

    fun of(value: Int): Op = when (value) {
        1 -> Op(0, false); 2 -> Op(0, true)
        3 -> Op(180, false); 4 -> Op(180, true)
        5 -> Op(90, true); 6 -> Op(90, false)
        7 -> Op(270, true); 8 -> Op(270, false)
        else -> throw IllegalArgumentException("orientation " + value + " outside 1..8 (corrupt EXIF; refusing beats flipping)")
    }

    fun swapsDimensions(value: Int): Boolean = of(value).rotateCw % 180 == 90

    fun displayedSize(value: Int, width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "degenerate image" }
        return if (swapsDimensions(value)) height to width else width to height
    }

    /** The value that undoes this one on display. */
    fun inverse(value: Int): Int {
        val op = of(value)
        if (!op.mirrorHorizontal) {
            val rot = (360 - op.rotateCw) % 360
            return intArrayOf(1, 6, 3, 8)[rot / 90]
        }
        return value   // mirrored forms are self-inverse under display semantics
    }
}
