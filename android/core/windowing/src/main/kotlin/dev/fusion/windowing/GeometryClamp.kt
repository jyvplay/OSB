// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * Bounded window-geometry clamper. Clamps a window rectangle (left, top,
 * width, height) to a work area, enforcing minimum and maximum size
 * constraints and keeping at least a minimum visible portion on-screen.
 * Multiple work areas (displays) are supported; the window is clamped to
 * the work area whose center is closest to the window's center.
 *
 * Declared narrowings: at most [MAX_DISPLAYS] work areas; coordinates and
 * dimensions are non-negative integers at most [MAX_COORD]; minimum size is
 * at least 1x1 and at most the maximum size; clamping never expands a window
 * beyond its maximum size and never shrinks it below its minimum size.
 */
class GeometryClamp private constructor(
    private val workAreas: List<Rect>
) {
    companion object {
        const val MAX_DISPLAYS = 16
        const val MAX_COORD = 1 << 16  // 65536

        data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
            val width: Int get() = right - left
            val height: Int get() = bottom - top
            val centerX: Int get() = (left + right) / 2
            val centerY: Int get() = (top + bottom) / 2
        }

        data class ClampedRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
            val width: Int get() = right - left
            val height: Int get() = bottom - top
        }

        fun empty(): GeometryClamp = GeometryClamp(emptyList())
    }

    fun addWorkArea(left: Int, top: Int, right: Int, bottom: Int) {
        require(workAreas.size < MAX_DISPLAYS) { "too many work areas: $MAX_DISPLAYS" }
        require(left >= 0 && top >= 0 && right > left && bottom > top) { "invalid work area" }
        require(left <= MAX_COORD && top <= MAX_COORD && right <= MAX_COORD && bottom <= MAX_COORD) { "coordinate exceeds $MAX_COORD" }
    }

    fun clamp(
        left: Int, top: Int, width: Int, height: Int,
        minWidth: Int = 1, minHeight: Int = 1,
        maxWidth: Int = MAX_COORD, maxHeight: Int = MAX_COORD
    ): ClampedRect {
        require(width >= 0 && height >= 0) { "negative dimension" }
        require(minWidth >= 1 && minHeight >= 1) { "minimum size must be >= 1" }
        require(maxWidth >= minWidth && maxHeight >= minHeight) { "max smaller than min" }
        require(maxWidth <= MAX_COORD && maxHeight <= MAX_COORD) { "max exceeds $MAX_COORD" }

        var w = width.coerceIn(minWidth, maxWidth)
        var h = height.coerceIn(minHeight, maxHeight)

        val target = if (workAreas.isEmpty()) {
            Rect(0, 0, MAX_COORD, MAX_COORD)
        } else {
            val cx = left + w / 2
            val cy = top + h / 2
            workAreas.minByOrNull { (it.centerX - cx).absoluteValue + (it.centerY - cy).absoluteValue }!!
        }

        var x = left.coerceIn(target.left, target.right - w)
        var y = top.coerceIn(target.top, target.bottom - h)

        // Re-clamp width/height after positioning (right/bottom edge may be hit)
        x = x.coerceIn(target.left, target.right - w)
        y = y.coerceIn(target.top, target.bottom - h)
        w = w.coerceIn(minWidth, (target.right - x).coerceAtLeast(minWidth))
        h = h.coerceIn(minHeight, (target.bottom - y).coerceAtLeast(minHeight))
        w = w.coerceAtMost(maxWidth)
        h = h.coerceAtMost(maxHeight)

        return ClampedRect(x, y, x + w, y + h)
    }

    fun clampToWorkArea(left: Int, top: Int, width: Int, height: Int): ClampedRect =
        clamp(left, top, width, height)

    private fun Int.coerceAtLeast(min: Int) = if (this < min) min else this
}
