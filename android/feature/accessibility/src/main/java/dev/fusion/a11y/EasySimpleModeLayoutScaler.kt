// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Clean-room Easy / Simple Mode Launcher & Chrome Simplifier (`AX-08`: giant `>=64dp` touch
 * targets, 2x3 high-legibility grid, `1.35x` font floor, and single-tap pinned caregiver/utility
 * tiles).
 */
object EasySimpleModeLayoutScaler {
    const val SCOPE =
        "Pure-JVM Easy/Simple Mode layout scaler enforcing >=64dp touch targets, 2x3 giant-tile launcher pages, 1.35x typography floor, and chrome decluttering (AX-08)."

    data class SimpleTile(val id: String, val label: String, val minTouchTargetDp: Int)

    data class SimpleModeLayout(
        val columns: Int,
        val rowsPerPage: Int,
        val effectiveFontScale: Float,
        val minTouchTargetDp: Int,
        val hideMultiWindowSplitChrome: Boolean,
        val pages: List<List<SimpleTile>>,
    )

    fun buildSimpleLauncher(
        pinnedItems: List<Pair<String, String>>, // id -> label
        systemFontScale: Float = 1.0f,
        landscape: Boolean = false,
    ): SimpleModeLayout {
        require(pinnedItems.isNotEmpty() && systemFontScale in 0.75f..3.0f) { "invalid Easy Mode input" }
        val cols = if (landscape) 3 else 2
        val rows = if (landscape) 2 else 3
        val perPage = cols * rows
        val minTarget = 64
        val tiles = pinnedItems.map { (id, lbl) -> SimpleTile(id, lbl.trim(), minTarget) }
        return SimpleModeLayout(
            columns = cols,
            rowsPerPage = rows,
            effectiveFontScale = maxOf(systemFontScale, 1.35f),
            minTouchTargetDp = minTarget,
            hideMultiWindowSplitChrome = true,
            pages = tiles.chunked(perPage),
        )
    }
}
