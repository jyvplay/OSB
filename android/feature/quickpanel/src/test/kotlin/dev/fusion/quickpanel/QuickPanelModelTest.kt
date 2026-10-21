// SPDX-License-Identifier: Apache-2.0
package dev.fusion.quickpanel

import dev.fusion.settings.Appearance
import dev.fusion.settings.DesktopLayout
import dev.fusion.settings.ProfessionalPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPanelModelTest {
    @Test
    fun presetsAndAmnesicPurgeAdvanceEpochAndZeroConsents() {
        val s0 = QuickPanelSnapshot(Appearance(), activeConsentCount = 3, openWindowCount = 2, amnesicWipeGeneration = 0L)
        val art = s0.applyPreset(StudioPreset.ART_SILVER)
        assertEquals(ProfessionalPalette.STUDIO_SILVER, art.appearance.palette)
        assertEquals(DesktopLayout.ART, art.appearance.layout)
        val purged = art.triggerAmnesicPurge()
        assertEquals(0, purged.activeConsentCount)
        assertEquals(1L, purged.amnesicWipeGeneration)
    }
}
