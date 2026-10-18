// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.min

/** Layout budgets, not a beauty certificate. Text scaling increases slots; capacity never shrinks a target. */
object DesktopMetrics {
    const val MIN_TARGET = 48.0
    const val GAP = 8.0
    const val PHI = 1.618033988749895 // Optional composition proportion, NOT an accessibility rule.
    data class Dock(val slot: Int, val glyph: Int, val height: Int, val gap: Int, val viewport: Double)
    fun dock(width: Double, fontScale: Double, keyboard: Boolean, items: Int): Dock {
        require(width.isFinite() && width >= 48 && fontScale.isFinite() && fontScale in .5..5.0 && items in 0..64)
        val slot = if (fontScale > 1.5) 88 else if (fontScale > 1.15) 72 else 64
        val height = maxOf(if (keyboard) 64 else if (fontScale > 1.5) 96 else 80, kotlin.math.ceil((if (keyboard) 24 else 32) + 5 + 16 * fontScale + 8).toInt())
        // At most eight preferred visible slots. More targets scroll, rather than fill all available space.
        val viewport = min((width - 24).coerceAtLeast(48.0), 16.0 + items.coerceIn(1, 8) * (slot + GAP) - GAP)
        return Dock(slot, if (keyboard) 24 else 32, height, GAP.toInt(), viewport)
    }
    data class Bounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        init { require(listOf(left, top, right, bottom).all { it.isFinite() } && right >= left && bottom >= top) }
        val width get() = right - left
        val height get() = bottom - top
        fun inside(parent: Bounds, tolerance: Double = .01) = left >= parent.left - tolerance && top >= parent.top - tolerance && right <= parent.right + tolerance && bottom <= parent.bottom + tolerance
    }
    fun targetViolations(bounds: Bounds, viewport: Bounds): List<String> = buildList {
        if (bounds.width + .01 < MIN_TARGET || bounds.height + .01 < MIN_TARGET) add("small-target")
        if (!bounds.inside(viewport)) add("clipped-target")
    }
}
