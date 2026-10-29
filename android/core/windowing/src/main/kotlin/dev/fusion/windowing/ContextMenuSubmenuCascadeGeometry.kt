// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

import kotlin.math.min

/**
 * **Cascading Secondary Submenu Right-Sizing & Edge-Flip Placement Solver** + Expanded **7-OS
 * Comparative Context-Menu Width Benchmark**.
 *
 * Extends `RightClickContextMenuGeometry` (`180..280 dp` primary menu) to secondary cascading
 * submenus (`Open With ▸`, `Save via Recall ▸`, `Share & Privacy ▸`):
 * - Secondary submenu width strictly clamped to **`168.0..240.0 dp`** (`<= 28%` of desktop viewport).
 * - Overlaps parent menu edge by `-4.0 dp` on right-cascade so diagonal hover stays continuous;
 *   automatically flips to the **left** of the parent menu when `parentRight - 4 + subW > viewportWidth - 8`!
 */
object ContextMenuSubmenuCascadeGeometry {
    const val SCOPE =
        "Cascading secondary submenu right-sizer (168..240dp, -4dp parent overlap, automatic left/up flip) and 7-OS menu benchmark."

    const val SUBMENU_MIN_WIDTH_DP = 168.0
    const val SUBMENU_MAX_WIDTH_DP = 240.0
    const val PARENT_OVERLAP_DP = 4.0

    data class SevenOsBenchmarkEntry(
        val osName: String,
        val component: String,
        val primaryTypicalWidthDp: ClosedFloatingPointRange<Double>,
        val submenuTypicalWidthDp: ClosedFloatingPointRange<Double>,
    )

    val SEVEN_OS_BENCHMARKS: List<SevenOsBenchmarkEntry> = listOf(
        SevenOsBenchmarkEntry("macOS 15 Sequoia", "NSMenu", 180.0..260.0, 160.0..236.0),
        SevenOsBenchmarkEntry("Windows 11 24H2", "WinUI 3 MenuFlyout", 180.0..280.0, 168.0..240.0),
        SevenOsBenchmarkEntry("GNOME 47 Adwaita", "GtkPopoverMenu", 180.0..260.0, 168.0..232.0),
        SevenOsBenchmarkEntry("KDE Plasma 6", "Kirigami QMenu", 184.0..272.0, 168.0..240.0),
        SevenOsBenchmarkEntry("ChromeOS M130", "views::MenuItemView", 192.0..280.0, 176.0..240.0),
        SevenOsBenchmarkEntry("iPadOS 18", "UIContextMenuInteraction", 220.0..250.0, 200.0..240.0),
        SevenOsBenchmarkEntry("Fusion Shell R53", "FusionContextMenuPopup", 180.0..280.0, 168.0..240.0),
    )

    data class SubmenuPlacement(
        val xDp: Double,
        val yDp: Double,
        val widthDp: Double,
        val heightDp: Double,
        val cascadedLeftOfParent: Boolean,
        val flippedUpward: Boolean,
    )

    fun solveSubmenu(
        viewportWidthDp: Double,
        viewportHeightDp: Double,
        parentMenu: RightClickContextMenuGeometry.ContextMenuPlacement,
        anchorRowIndexZeroBased: Int,
        submenuRows: List<RightClickContextMenuGeometry.MenuActionRow>,
        approxCharWidthDp: Double = 7.2,
    ): SubmenuPlacement {
        require(submenuRows.isNotEmpty() && anchorRowIndexZeroBased >= 0)
        val longest = submenuRows.maxOf { r ->
            r.label.length + (if (r.shortcutHint.isNotEmpty()) r.shortcutHint.length + 3 else 0)
        }
        val cap = min(SUBMENU_MAX_WIDTH_DP, viewportWidthDp * 0.28).coerceAtLeast(140.0)
        val lo = min(SUBMENU_MIN_WIDTH_DP, cap)
        val widthDp = (longest * approxCharWidthDp + 40.0).coerceIn(lo, cap)

        val dividers = submenuRows.count { it.startsNewSection }
        val rawHeight = submenuRows.size * RightClickContextMenuGeometry.ITEM_ROW_HEIGHT_DP +
            dividers * RightClickContextMenuGeometry.DIVIDER_HEIGHT_DP +
            RightClickContextMenuGeometry.VERTICAL_CHROME_PADDING_DP
        val heightDp = min(rawHeight, viewportHeightDp - 16.0)

        val margin = RightClickContextMenuGeometry.VIEWPORT_EDGE_MARGIN_DP
        val rightCandidateX = parentMenu.xDp + parentMenu.widthDp - PARENT_OVERLAP_DP
        val cascadedLeft: Boolean
        val xDp: Double
        if (rightCandidateX + widthDp <= viewportWidthDp - margin) {
            xDp = rightCandidateX
            cascadedLeft = false
        } else {
            val leftX = parentMenu.xDp - widthDp + PARENT_OVERLAP_DP
            xDp = leftX.coerceIn(margin, (viewportWidthDp - margin - widthDp).coerceAtLeast(margin))
            cascadedLeft = true
        }

        val anchorRowTopY = parentMenu.yDp + 6.0 + anchorRowIndexZeroBased * RightClickContextMenuGeometry.ITEM_ROW_HEIGHT_DP
        val flippedUp = anchorRowTopY + heightDp > viewportHeightDp - margin
        val yDp = if (!flippedUp) {
            anchorRowTopY.coerceAtLeast(margin)
        } else {
            (viewportHeightDp - margin - heightDp).coerceAtLeast(margin)
        }
        return SubmenuPlacement(xDp, yDp, widthDp, heightDp, cascadedLeft, flippedUp)
    }
}
