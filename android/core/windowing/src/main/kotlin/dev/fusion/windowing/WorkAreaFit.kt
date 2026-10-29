// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * Clean-room multi-inset desktop work-area solver, off-screen titlebar rescue, and wrapped
 * window cascade placer for `:core:windowing`.
 *
 * Ensures freeform windows never hide their drag handle under system status bars, navigation
 * docks, or soft-keyboard (IME) insets, and never become ungrabbable when moved near screen edges.
 */
object WorkAreaFit {
    const val SCOPE =
        "Single-display multi-inset work-area computation, min-size window clamping, titlebar grab-handle rescue, and wrapped cascade placement; multi-monitor spanning lives in DisplayTopology."

    const val DEFAULT_TITLEBAR_HEIGHT = 36
    const val DEFAULT_MIN_GRAB_WIDTH = 96

    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        init {
            require(width > 0 && height > 0) { "non-positive rect dimensions ${width}x$height" }
        }
        val right: Int get() = x + width
        val bottom: Int get() = y + height
    }

    data class Insets(
        val left: Int = 0,
        val top: Int = 0,
        val right: Int = 0,
        val bottom: Int = 0,
    ) {
        init {
            require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) { "negative inset refused" }
        }
    }

    fun usableWorkArea(screenWidth: Int, screenHeight: Int, insetsList: List<Insets>): Rect {
        require(screenWidth > 0 && screenHeight > 0) { "invalid screen ${screenWidth}x$screenHeight" }
        // System insets on Android/Wayland combine via component-wise max (e.g. bottom = max(dock, IME)).
        var l = 0; var t = 0; var r = 0; var b = 0
        for (ins in insetsList) {
            if (ins.left > l) l = ins.left
            if (ins.top > t) t = ins.top
            if (ins.right > r) r = ins.right
            if (ins.bottom > b) b = ins.bottom
        }
        val w = screenWidth - l - r
        val h = screenHeight - t - b
        require(w >= DEFAULT_MIN_GRAB_WIDTH && h >= DEFAULT_TITLEBAR_HEIGHT) {
            "insets ($l,$t,$r,$b) leave unusable work area ${w}x$h on ${screenWidth}x$screenHeight"
        }
        return Rect(l, t, w, h)
    }

    /**
     * Clamps [desired] size to `[minWidth..work.width]` x `[minHeight..work.height]` and rescues
     * position so the window's top titlebar (`titlebarHeight` tall) stays fully vertically inside
     * [work] and at least [minGrabWidth] horizontally overlaps [work].
     */
    fun clampWindow(
        desired: Rect,
        work: Rect,
        minWidth: Int = 240,
        minHeight: Int = 160,
        titlebarHeight: Int = DEFAULT_TITLEBAR_HEIGHT,
        minGrabWidth: Int = DEFAULT_MIN_GRAB_WIDTH,
    ): Rect {
        require(minWidth > 0 && minHeight > 0) { "min dimensions must be > 0" }
        require(titlebarHeight > 0 && minGrabWidth > 0) { "grab dimensions must be > 0" }

        val effMinW = minOf(minWidth, work.width)
        val effMinH = minOf(minHeight, work.height)
        val w = desired.width.coerceIn(effMinW, work.width)
        val h = desired.height.coerceIn(effMinH, work.height)

        val grabW = minOf(minGrabWidth, w, work.width)
        val minX = work.x - w + grabW
        val maxX = work.right - grabW
        val x = desired.x.coerceIn(minX, maxX)

        val maxTitleY = (work.bottom - minOf(titlebarHeight, h)).coerceAtLeast(work.y)
        val y = desired.y.coerceIn(work.y, maxTitleY)
        return Rect(x, y, w, h)
    }

    fun cascade(
        work: Rect,
        windowWidth: Int,
        windowHeight: Int,
        count: Int,
        stepPx: Int = 32,
    ): List<Rect> {
        require(count in 0..256) { "cascade count $count outside 0..256" }
        require(stepPx > 0) { "stepPx $stepPx must be > 0" }
        if (count == 0) return emptyList()
        val w = windowWidth.coerceIn(1, work.width)
        val h = windowHeight.coerceIn(1, work.height)
        val maxStepsX = (work.width - w) / stepPx
        val maxStepsY = (work.height - h) / stepPx
        val cycle = minOf(maxStepsX, maxStepsY) + 1

        return List(count) { idx ->
            val slot = idx % cycle
            Rect(work.x + slot * stepPx, work.y + slot * stepPx, w, h)
        }
    }
}
