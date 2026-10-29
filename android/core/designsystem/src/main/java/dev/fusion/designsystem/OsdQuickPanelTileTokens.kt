// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

/**
 * Clean-room Quick Panel Control Center Tile Reducer (`NC-03`: audio, brightness, media, DND,
 * night-light, battery, Beam, radio airplane/Wi-Fi/BT) and Volume / Brightness / Keyboard-Layout
 * HUD OSD Overlay Tokens (`NC-06`).
 */
object OsdQuickPanelTileTokens {
    const val SCOPE =
        "Pure-JVM Quick Panel 8-domain control reducer (NC-03) and HUD OSD overlay geometry & auto-dismiss token calculator (NC-06)."

    enum class OsdKind { VOLUME, BRIGHTNESS, KEYBOARD_LAYOUT }

    data class QuickPanelState(
        val mediaVolumePct: Int = 60,
        val brightnessPct: Int = 55,
        val mediaPlaying: Boolean = false,
        val dndEnabled: Boolean = false,
        val nightLightWarmthPct: Int = 0,
        val batterySaverEnabled: Boolean = false,
        val beamTargetName: String? = null,
        val wifiEnabled: Boolean = true,
        val bluetoothEnabled: Boolean = false,
    )

    data class OsdBannerSpec(
        val kind: OsdKind,
        val label: String,
        val progress0To1: Double?,
        val widthDp: Int,
        val heightDp: Int,
        val autoDismissMs: Long,
    )

    fun applyQuickPanelAction(state: QuickPanelState, action: String, intArg: Int = 0, strArg: String? = null): QuickPanelState =
        when (action.uppercase()) {
            "SET_VOLUME" -> state.copy(mediaVolumePct = intArg.coerceIn(0, 100))
            "SET_BRIGHTNESS" -> state.copy(brightnessPct = intArg.coerceIn(5, 100))
            "TOGGLE_MEDIA" -> state.copy(mediaPlaying = !state.mediaPlaying)
            "TOGGLE_DND" -> state.copy(dndEnabled = !state.dndEnabled)
            "SET_NIGHT_LIGHT" -> state.copy(nightLightWarmthPct = intArg.coerceIn(0, 100))
            "TOGGLE_BATTERY_SAVER" -> state.copy(batterySaverEnabled = !state.batterySaverEnabled)
            "SET_BEAM_TARGET" -> state.copy(beamTargetName = strArg?.takeIf { it.isNotBlank() })
            "AIRPLANE_LOCKDOWN" -> state.copy(wifiEnabled = false, bluetoothEnabled = false, beamTargetName = null)
            else -> throw IllegalArgumentException("unknown quick-panel action '$action'")
        }

    fun buildOsdBanner(kind: OsdKind, levelPct: Int = 50, layoutTag: String = "en-US"): OsdBannerSpec = when (kind) {
        OsdKind.VOLUME -> {
            val clamped = levelPct.coerceIn(0, 100)
            OsdBannerSpec(kind, if (clamped == 0) "Muted" else "Volume $clamped%", clamped / 100.0, 260, 56, 1800L)
        }
        OsdKind.BRIGHTNESS -> {
            val clamped = levelPct.coerceIn(5, 100)
            OsdBannerSpec(kind, "Display $clamped%", clamped / 100.0, 260, 56, 1800L)
        }
        OsdKind.KEYBOARD_LAYOUT -> OsdBannerSpec(kind, "Layout: $layoutTag", null, 240, 48, 1500L)
    }
}
