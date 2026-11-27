// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

import kotlin.math.absoluteValue

/**
 * Bounded window-geometry clamper. Clamps a window rectangle (left, top,
 * width, height) to a work area, enforcing minimum and maximum size
 * constraints and full containment. A minimum that fits no display refuses.
 * Multiple work areas (displays) are supported; the window is clamped to
 * the fitting work area whose center has minimum Manhattan distance to the
 * requested window center (ties choose first). Arbitrary Int offscreen positions
 * are admitted; distance arithmetic uses Long. Instance access is single-threaded.
 *
 * Declared narrowings: at most [MAX_DISPLAYS] work areas; coordinates and
 * dimensions are non-negative integers at most [MAX_COORD]; minimum size is
 * at least 1x1 and at most the maximum size; clamping never expands a window
 * beyond its maximum size and never shrinks it below its minimum size.
 */
class GeometryClamp private constructor(
    private val workAreas: MutableList<Rect>
) {
    companion object {
        const val MAX_DISPLAYS = 16
        const val MAX_COORD = 1 shl 16  // 65536

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

        fun empty(): GeometryClamp = GeometryClamp(mutableListOf())
    }

    fun addWorkArea(left: Int, top: Int, right: Int, bottom: Int) {
        require(workAreas.size < MAX_DISPLAYS) { "too many work areas: $MAX_DISPLAYS" }
        require(left >= 0 && top >= 0 && right > left && bottom > top) { "invalid work area" }
        require(left <= MAX_COORD && top <= MAX_COORD && right <= MAX_COORD && bottom <= MAX_COORD) { "coordinate exceeds $MAX_COORD" }
        workAreas.add(Rect(left, top, right, bottom))
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
        val candidates = if (workAreas.isEmpty()) listOf(Rect(0, 0, MAX_COORD, MAX_COORD)) else workAreas.toList()
        val fitting = candidates.filter { it.width >= minWidth && it.height >= minHeight }
        require(fitting.isNotEmpty()) { "no work area can contain minimum size" }
        val cx = left.toLong() + w / 2
        val cy = top.toLong() + h / 2
        val target = fitting.minBy { (it.centerX.toLong() - cx).absoluteValue + (it.centerY.toLong() - cy).absoluteValue }
        w = minOf(w, target.width)
        h = minOf(h, target.height)
        val x = left.coerceIn(target.left, target.right - w)
        val y = top.coerceIn(target.top, target.bottom - h)
        return ClampedRect(x, y, x + w, y + h)
    }

    fun clampToWorkArea(left: Int, top: Int, width: Int, height: Int): ClampedRect =
        clamp(left, top, width, height)

}
