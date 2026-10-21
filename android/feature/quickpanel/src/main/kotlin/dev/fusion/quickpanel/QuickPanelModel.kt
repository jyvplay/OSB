// SPDX-License-Identifier: Apache-2.0
package dev.fusion.quickpanel

import dev.fusion.settings.Appearance
import dev.fusion.settings.DesktopLayout
import dev.fusion.settings.DesktopScene
import dev.fusion.settings.ProfessionalPalette

/**
 * Deterministic Desktop Quick Panel state & preset transitions (`:feature:quickpanel`).
 *
 * Provides one-click switching between Industry-Standard Silver Art (`STUDIO_SILVER` + `ART` + `TABLETOP`),
 * Crisp Light Business (`OFFICE_BLUE` + `BUSINESS` + `TABLETOP`), Dark Studio (`STUDIO_GRAPHITE`),
 * 3D Slanted Tabletop vs Flat Desktop projection, and immediate Amnesic Session Purge.
 */
enum class StudioPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val appearance: Appearance
) {
    ART_SILVER(
        "art-silver",
        "Art · Studio Silver",
        "Brushed silver chrome, centered magnification dock, 83% 3D tabletop",
        Appearance(ProfessionalPalette.STUDIO_SILVER, DesktopLayout.ART, dockZoom = true, scene = DesktopScene.TABLETOP)
    ),
    BUSINESS_LIGHT(
        "business-light",
        "Business · Office Light",
        "Crisp executive light surfaces, full-width taskbar, 83% 3D tabletop",
        Appearance(ProfessionalPalette.OFFICE_BLUE, DesktopLayout.BUSINESS, dockZoom = false, scene = DesktopScene.TABLETOP)
    ),
    STUDIO_DARK(
        "studio-dark",
        "Studio · Graphite Dark",
        "Low-glare dark slate surfaces with WCAG AA + APCA calibrated ink",
        Appearance(ProfessionalPalette.STUDIO_GRAPHITE, DesktopLayout.ART, dockZoom = true, scene = DesktopScene.TABLETOP)
    )
}

data class QuickPanelSnapshot(
    val appearance: Appearance,
    val activeConsentCount: Int,
    val openWindowCount: Int,
    val amnesicWipeGeneration: Long,
    val usbHidQuarantined: Boolean = false
) {
    val activePreset: StudioPreset?
        get() = StudioPreset.entries.firstOrNull {
            it.appearance.palette == appearance.palette && it.appearance.layout == appearance.layout
        }

    fun applyPreset(preset: StudioPreset): QuickPanelSnapshot =
        copy(appearance = preset.appearance.copy(scene = appearance.scene))

    fun toggleSceneMode(): QuickPanelSnapshot = copy(
        appearance = appearance.copy(
            scene = if (appearance.scene == DesktopScene.TABLETOP) DesktopScene.FLAT else DesktopScene.TABLETOP
        )
    )

    fun triggerAmnesicPurge(): QuickPanelSnapshot = copy(
        activeConsentCount = 0,
        amnesicWipeGeneration = amnesicWipeGeneration + 1
    )
}
