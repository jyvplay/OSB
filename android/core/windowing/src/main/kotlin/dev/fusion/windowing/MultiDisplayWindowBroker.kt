// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * Clean-room Multi-Display Window Broker, Remembered Geometry Rules, Always-On-Top Mini Windows,
 * Distraction-Free Focus Mode, and External-Display Cursor Confinement (`WM-04`, `WM-07`, `WM-08`,
 * `WM-10`, `WM-11`, `WM-16`).
 */
object MultiDisplayWindowBroker {
    enum class ZLayer { NORMAL, DISTRACTION_FREE_SOLO, ALWAYS_ON_TOP_MINI }

    data class DisplayBounds(val displayId: Int, val widthDp: Int, val heightDp: Int) {
        init {
            require(widthDp >= 320 && heightDp >= 320) { "display bounds < 320dp" }
        }
    }

    data class WindowGeometryRule(
        val appOrModuleId: String,
        val preferredDisplayId: Int?,
        val widthDp: Int,
        val heightDp: Int,
        val alwaysOnTopMini: Boolean = false,
    )

    data class ManagedWindow(
        val windowId: String,
        val appOrModuleId: String,
        val displayId: Int,
        val xDp: Int,
        val yDp: Int,
        val widthDp: Int,
        val heightDp: Int,
        val zLayer: ZLayer = ZLayer.NORMAL,
        val hiddenByFocusMode: Boolean = false,
    )

    fun spawnWithRememberedRule(
        windowId: String,
        appOrModuleId: String,
        actedDisplayId: Int,
        displays: Map<Int, DisplayBounds>,
        rules: Map<String, WindowGeometryRule>,
    ): ManagedWindow {
        val rule = rules[appOrModuleId]
        val targetDisplayId = rule?.preferredDisplayId?.takeIf { it in displays } ?: actedDisplayId
        val disp = displays[targetDisplayId] ?: displays.values.first()
        val isMini = rule?.alwaysOnTopMini ?: false
        val w = (rule?.widthDp ?: if (isMini) 320 else 720).coerceIn(240, disp.widthDp)
        val h = (rule?.heightDp ?: if (isMini) 220 else 520).coerceIn(180, disp.heightDp)
        val x = ((disp.widthDp - w) / 2).coerceAtLeast(0)
        val y = ((disp.heightDp - h) / 2).coerceAtLeast(0)
        return ManagedWindow(
            windowId = windowId,
            appOrModuleId = appOrModuleId,
            displayId = disp.displayId,
            xDp = x,
            yDp = y,
            widthDp = w,
            heightDp = h,
            zLayer = if (isMini) ZLayer.ALWAYS_ON_TOP_MINI else ZLayer.NORMAL,
        )
    }

    /** Re-homes windows when an external display is hot-unplugged (`WM-07`), clamping inside primary display. */
    fun handleDisplayDisconnect(
        windows: List<ManagedWindow>,
        disconnectedDisplayId: Int,
        fallbackDisplay: DisplayBounds,
    ): List<ManagedWindow> = windows.map { w ->
        if (w.displayId != disconnectedDisplayId) {
            w
        } else {
            val nw = w.widthDp.coerceAtMost(fallbackDisplay.widthDp)
            val nh = w.heightDp.coerceAtMost(fallbackDisplay.heightDp)
            w.copy(
                displayId = fallbackDisplay.displayId,
                widthDp = nw,
                heightDp = nh,
                xDp = w.xDp.coerceIn(0, (fallbackDisplay.widthDp - nw).coerceAtLeast(0)),
                yDp = w.yDp.coerceIn(0, (fallbackDisplay.heightDp - nh).coerceAtLeast(0)),
            )
        }
    }

    /** Toggles distraction-free focus mode (`WM-10`), hiding background normal windows while keeping PIP mini windows optional. */
    fun applyDistractionFreeFocus(
        windows: List<ManagedWindow>,
        focusedWindowId: String?,
        keepMiniWindowsVisible: Boolean = true,
    ): List<ManagedWindow> {
        if (focusedWindowId == null) {
            return windows.map {
                it.copy(
                    zLayer = if (it.zLayer == ZLayer.DISTRACTION_FREE_SOLO) ZLayer.NORMAL else it.zLayer,
                    hiddenByFocusMode = false,
                )
            }
        }
        return windows.map { w ->
            when {
                w.windowId == focusedWindowId -> w.copy(zLayer = ZLayer.DISTRACTION_FREE_SOLO, hiddenByFocusMode = false)
                w.zLayer == ZLayer.ALWAYS_ON_TOP_MINI && keepMiniWindowsVisible -> w.copy(hiddenByFocusMode = false)
                else -> w.copy(hiddenByFocusMode = true)
            }
        }
    }

    /** Confines pointer coordinates strictly inside external display bounds (`WM-16`). */
    fun confineCursorToDisplay(xPx: Int, yPx: Int, widthPx: Int, heightPx: Int, confineEnabled: Boolean): Pair<Int, Int> {
        if (!confineEnabled) return xPx to yPx
        return xPx.coerceIn(0, (widthPx - 1).coerceAtLeast(0)) to yPx.coerceIn(0, (heightPx - 1).coerceAtLeast(0))
    }
}
