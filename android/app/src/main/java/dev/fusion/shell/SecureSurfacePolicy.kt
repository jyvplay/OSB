// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Pure-JVM window security & anti-tapjacking policy (`SecureSurfacePolicy`).
 *
 * Computes exact Android `Window` / `View` defense flags per shell destination:
 * - `flagSecure` (`WindowManager.LayoutParams.FLAG_SECURE` = `0x00002000`): blocks screenshots,
 *   MediaProjection screen recording, Recent-Apps thumbnails, and non-secure wireless displays.
 * - `hideOverlayWindows` (Android 12+ `Window.setHideOverlayWindows(true)`): evicts non-system
 *   `TYPE_APPLICATION_OVERLAY` floating windows to defeat overlay keyloggers and tapjacking!
 * - `filterTouchesWhenObscured` (`MotionEvent.FLAG_WINDOW_IS_OBSCURED` / `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`):
 *   rejects synthetic/obscured touch events on sensitive surfaces.
 */
object SecureSurfacePolicy {
    const val SCOPE =
        "Pure-JVM Window FLAG_SECURE + Android 12+ hideOverlayWindows + obscured-touch tapjacking filter matrix across shell routes; SurfaceFlinger HAL composition out of scope."

    const val ANDROID_FLAG_SECURE = 0x00002000
    const val MOTION_FLAG_WINDOW_IS_OBSCURED = 0x00000001
    const val MOTION_FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x00000002

    enum class SurfaceRoute {
        LAUNCHER_HOME,
        WRITER_PRIVATE_DOC,
        SPREADSHEET_LEDGER,
        TOR_ONION_BROWSER,
        VAULT_PASSPHRASE_PROMPT,
        PANIC_WIPE_CONFIRM,
        PUBLIC_MEDIA_CAST,
    }

    data class WindowDefenseSpec(
        val route: SurfaceRoute,
        val flagSecure: Boolean,
        val hideOverlayWindows: Boolean,
        val rejectObscuredTouches: Boolean,
        val recentsBlanking: Boolean,
    ) {
        val windowFlagsMask: Int get() = if (flagSecure) ANDROID_FLAG_SECURE else 0

        fun shouldAcceptMotionEvent(motionEventFlags: Int): Boolean {
            if (!rejectObscuredTouches) return true
            val obscured = (motionEventFlags and
                (MOTION_FLAG_WINDOW_IS_OBSCURED or MOTION_FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) != 0
            return !obscured
        }
    }

    fun forRoute(route: SurfaceRoute, globalAmnesicLockdown: Boolean = false): WindowDefenseSpec = when (route) {
        SurfaceRoute.VAULT_PASSPHRASE_PROMPT,
        SurfaceRoute.PANIC_WIPE_CONFIRM,
        SurfaceRoute.TOR_ONION_BROWSER,
        -> WindowDefenseSpec(
            route = route,
            flagSecure = true,
            hideOverlayWindows = true,
            rejectObscuredTouches = true,
            recentsBlanking = true,
        )
        SurfaceRoute.WRITER_PRIVATE_DOC,
        SurfaceRoute.SPREADSHEET_LEDGER,
        -> WindowDefenseSpec(
            route = route,
            flagSecure = true,
            hideOverlayWindows = true,
            rejectObscuredTouches = true,
            recentsBlanking = true,
        )
        SurfaceRoute.LAUNCHER_HOME -> WindowDefenseSpec(
            route = route,
            flagSecure = globalAmnesicLockdown,
            hideOverlayWindows = globalAmnesicLockdown,
            rejectObscuredTouches = true,
            recentsBlanking = globalAmnesicLockdown,
        )
        SurfaceRoute.PUBLIC_MEDIA_CAST -> WindowDefenseSpec(
            route = route,
            flagSecure = globalAmnesicLockdown,
            hideOverlayWindows = false,
            rejectObscuredTouches = false,
            recentsBlanking = globalAmnesicLockdown,
        )
    }
}
