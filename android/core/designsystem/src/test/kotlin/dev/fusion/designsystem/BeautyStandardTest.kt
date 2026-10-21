// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import dev.fusion.settings.ProfessionalPalette
import org.junit.Assert.assertTrue
import org.junit.Test

class BeautyStandardTest {
    @Test
    fun satisfiesWcag22ApcaGoldenRatioAndAdaptiveDockBounds() {
        for (palette in ProfessionalPalette.entries) {
            val audit = BeautyStandard.auditPalette(ProfessionalPalettes.colors(palette))
            assertTrue("WCAG 2.2 AA failed for $palette", audit.passesWcag22Aa)
            assertTrue("APCA Lc failed for $palette", audit.passesApcaLabels)
        }
        val d = DesktopMetrics.dock(1280.0, 1.0, false, 20)
        assertTrue(d.slot >= 72 && d.viewport < 1280.0 * 0.60)
    }
}
