// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import dev.fusion.settings.ProfessionalPalette
import kotlin.math.abs
import kotlin.math.min

/**
 * Finite, named visual budgets. A passing audit is NOT a beauty certificate and NOT an
 * accessibility acceptance: it checks opaque sRGB math, minimum target sizes and named
 * composition proportions on the exact shipped constants. Rendered pixels, alpha blending,
 * HDR, font rendering, human preference and whole-page review remain outside this kernel.
 *
 * Threshold sources: WCAG 2.2 SC 1.4.3 (4.5:1 text / 3:1 large), SC 1.4.11 (3:1 non-text).
 * Phi is an optional composition proportion, never an accessibility rule.
 */
object BeautyStandard {
    const val AA_TEXT = 4.5
    const val AA_LARGE = 3.0
    const val AA_NON_TEXT = 3.0
    const val PHI = 1.618033988749895
    const val PHI_TOLERANCE = 0.05
    const val MIN_FLOOR_FRACTION = 0.80
    const val MIN_TARGET_DP = 48.0
    const val MAX_DOCK_SLOTS = 8

    data class PairAudit(val foregroundRole: String, val backgroundRole: String, val ratio: Double) {
        val aaText: Boolean get() = ratio >= AA_TEXT
        val aaLarge: Boolean get() = ratio >= AA_LARGE
    }

    /** Audits every semantic text pair of one shipped palette. Order matches [ProfessionalColors.textPairs]. */
    fun auditPalette(palette: ProfessionalPalette): List<PairAudit> {
        val c = ProfessionalPalettes.colors(palette)
        val roles = listOf("text/background", "text/surface", "text/raised", "muted/background",
            "muted/surface", "muted/raised", "onPrimary/primary", "onSelection/selection", "primary/background")
        return c.textPairs().mapIndexed { i, (fg, bg) ->
            val names = roles[i].split('/')
            PairAudit(names[0], names[1], Contrast.ratio(fg, bg))
        }
    }

    fun auditAllPalettes(): Map<ProfessionalPalette, List<PairAudit>> =
        ProfessionalPalette.entries.associateWith(::auditPalette)

    /** True when |ratio - phi| <= tolerance. Proportion hint only; see class note. */
    fun nearPhi(ratio: Double, tolerance: Double = PHI_TOLERANCE): Boolean {
        require(ratio.isFinite() && ratio > 0 && tolerance.isFinite() && tolerance in 0.0..1.0)
        return abs(ratio - PHI) <= tolerance
    }

    /**
     * Visually appealing dock sizing. Slots NEVER shrink to fit: at most [MAX_DOCK_SLOTS]
     * preferred visible slots are laid out, the rest scroll. Minimum 48dp targets preserved
     * at every font scale. Gaps prefer a phi-derived rhythm (gap ~= slot/phi^3) but are
     * clamped to a legible 4..16dp band so proportion never breaks usability.
     */
    data class DockPlan(val slotDp: Int, val gapDp: Int, val visibleSlots: Int, val overflow: Int, val viewportDp: Double)
    fun dockPlan(widthDp: Double, fontScale: Double, items: Int): DockPlan {
        require(widthDp.isFinite() && widthDp >= 48 && fontScale.isFinite() && fontScale in .5..5.0 && items in 0..64)
        val slot = if (fontScale > 1.5) 88 else if (fontScale > 1.15) 72 else 64
        val gap = (slot / (PHI * PHI * PHI)).coerceIn(4.0, 16.0).toInt().coerceAtLeast(4)
        val visible = min(items.coerceIn(0, MAX_DOCK_SLOTS), MAX_DOCK_SLOTS)
        val viewport = min((widthDp - 24).coerceAtLeast(48.0), 16.0 + visible.coerceAtLeast(1) * (slot + gap) - gap)
        return DockPlan(slot, gap, visible, (items - visible).coerceAtLeast(0), viewport)
    }

    /** Default slanted-tabletop contract: the floor must remain the dominant observable plane. */
    fun floorDominant(floorFraction: Double): Boolean {
        require(floorFraction.isFinite() && floorFraction in 0.0..1.0)
        return floorFraction >= MIN_FLOOR_FRACTION
    }

    /** Every control on a page must satisfy the minimum-target + inside-viewport budget. */
    fun pageTargetsOk(targets: List<DesktopMetrics.Bounds>, viewport: DesktopMetrics.Bounds): List<String> {
        require(targets.size in 1..512)
        return targets.flatMapIndexed { i, b -> DesktopMetrics.targetViolations(b, viewport).map { "$it@$i" } }
    }
}
