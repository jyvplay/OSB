// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import kotlin.math.roundToInt

/**
 * Multi-monitor DPI / fractional scale factor router for Fusion Desktop Mode (phone panel + USB-C
 * DisplayPort external monitor), snapping icon/chrome radii to integer device pixels when windows
 * cross monitor boundaries.
 */
object ShellMultiMonitorDpiScaleRouter {
    const val SCOPE =
        "Multi-display DPI scale router computing fractional density scale, integer pixel snap, and cross-monitor window bounds."

    data class MonitorSpec(val id: String, val widthPx: Int, val heightPx: Int, val dpi: Int)

    data class WindowScaleAssignment(
        val targetMonitorId: String,
        val densityScale: Double,
        val snappedChromeHeightPx: Int,
        val clampedWidthPx: Int,
        val clampedHeightPx: Int,
    )

    fun routeWindow(
        monitors: List<MonitorSpec>,
        preferredMonitorId: String,
        requestedWidthDp: Int,
        requestedHeightDp: Int,
        chromeHeightDp: Int = 40,
    ): WindowScaleAssignment {
        require(monitors.isNotEmpty())
        val mon = monitors.firstOrNull { it.id == preferredMonitorId } ?: monitors.first()
        require(mon.widthPx > 0 && mon.heightPx > 0 && mon.dpi in 72..800)
        // Quantize scale to 0.25x steps (1.0, 1.25, 1.5, 2.0, 2.5, 3.0...) for crisp vector rasterization
        val rawScale = mon.dpi / 160.0
        val quantizedScale = ((rawScale * 4.0).roundToInt().coerceAtLeast(4)) / 4.0
        val chromePx = (chromeHeightDp * quantizedScale).roundToInt().coerceAtLeast(24)
        val wPx = (requestedWidthDp * quantizedScale).roundToInt().coerceIn(320.coerceAtMost(mon.widthPx), mon.widthPx)
        val hPx = (requestedHeightDp * quantizedScale).roundToInt().coerceIn(240.coerceAtMost(mon.heightPx), mon.heightPx)
        return WindowScaleAssignment(mon.id, quantizedScale, chromePx, wPx, hPx)
    }
}
