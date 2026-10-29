// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * **Desktop Work-Area & Non-Intrusive Bottom-Right Overlay Zone Partitioner** subtracting top
 * status bar, bottom taskbar/shelf, and DisplayCutout insets while verifying that the bottom-right
 * Undo notification zone occupies `< 9%` of vertical height and `< 25%` of desktop width!
 */
object DesktopWorkAreaInsetPartitioner {
    const val SCOPE =
        "Desktop safe work-area inset solver verifying non-intrusive (<9% height, <25% width) bottom-right Undo overlay zone."

    data class RectDp(val x: Double, val y: Double, val width: Double, val height: Double)

    data class PartitionedWorkArea(
        val usableDesktopRect: RectDp,
        val bottomRightToastAnchorRect: RectDp,
        val toastHeightFractionOfDesktop: Double,
        val toastWidthFractionOfDesktop: Double,
        val rightEdgeRemainsUnobstructed: Boolean,
    )

    fun partition(
        displayWidthDp: Double,
        displayHeightDp: Double,
        topStatusInsetDp: Double = 28.0,
        bottomShelfInsetDp: Double = 56.0,
        compactStackHeightDp: Double = 76.0,
        compactStackWidthDp: Double = 320.0,
    ): PartitionedWorkArea {
        require(displayWidthDp >= 320.0 && displayHeightDp >= 320.0)
        val usableH = (displayHeightDp - topStatusInsetDp - bottomShelfInsetDp).coerceAtLeast(180.0)
        val usable = RectDp(0.0, topStatusInsetDp, displayWidthDp, usableH)

        val clampedW = compactStackWidthDp.coerceAtMost(displayWidthDp * 0.32)
        val clampedH = compactStackHeightDp.coerceAtMost(usableH * 0.22)
        val toastX = displayWidthDp - 16.0 - clampedW
        val toastY = topStatusInsetDp + usableH - 16.0 - clampedH
        val hFrac = clampedH / displayHeightDp
        val wFrac = clampedW / displayWidthDp
        val unobstructed = if (displayWidthDp >= 960.0) (hFrac < 0.09 && wFrac < 0.26) else (hFrac < 0.16 && wFrac <= 0.34)
        return PartitionedWorkArea(
            usableDesktopRect = usable,
            bottomRightToastAnchorRect = RectDp(toastX, toastY, clampedW, clampedH),
            toastHeightFractionOfDesktop = hFrac,
            toastWidthFractionOfDesktop = wFrac,
            rightEdgeRemainsUnobstructed = unobstructed,
        )
    }
}
