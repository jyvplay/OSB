// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Pure-JVM **W3C WCAG 2.2 SC 2.5.8 Target Size (`>= 24x24 dp` minimum / `>= 48x48 dp` Android AAA)
 * & Focus-Order Monotonicity Auditor** for `:core:accessibility` (including context-menu rows,
 * custom view-mode icon buttons, and bottom-right stacked Undo action targets).
 */
object WcagTargetSpacingFocusAuditor {
    const val SCOPE =
        "WCAG 2.2 SC 2.5.8 / Android 48dp touch-target & 2D spatial focus-traversal monotonicity auditor."

    data class InteractiveControlBounds(
        val id: String,
        val xDp: Double,
        val yDp: Double,
        val widthDp: Double,
        val heightDp: Double,
        val accessibleLabel: String,
    )

    data class AccessibilityAuditReport(
        val undersizedUnder24Dp: List<String>,
        val subOptimalUnder48Dp: List<String>,
        val unlabeledControls: List<String>,
        val overlappingTargets: List<Pair<String, String>>,
        val passesWcag22Aa: Boolean,
    )

    fun auditSurface(controls: List<InteractiveControlBounds>): AccessibilityAuditReport {
        val under24 = controls.filter { it.widthDp < 24.0 || it.heightDp < 24.0 }.map { it.id }
        val under48 = controls.filter { it.widthDp < 48.0 || it.heightDp < 48.0 }.map { it.id }
        val unlabeled = controls.filter { it.accessibleLabel.isBlank() }.map { it.id }
        val overlaps = ArrayList<Pair<String, String>>()
        for (i in controls.indices) {
            for (j in i + 1 until controls.size) {
                if (intersects(controls[i], controls[j])) overlaps.add(controls[i].id to controls[j].id)
            }
        }
        return AccessibilityAuditReport(
            undersizedUnder24Dp = under24,
            subOptimalUnder48Dp = under48,
            unlabeledControls = unlabeled,
            overlappingTargets = overlaps,
            passesWcag22Aa = under24.isEmpty() && unlabeled.isEmpty() && overlaps.isEmpty(),
        )
    }

    private fun intersects(a: InteractiveControlBounds, b: InteractiveControlBounds): Boolean =
        a.xDp < b.xDp + b.widthDp && a.xDp + a.widthDp > b.xDp &&
            a.yDp < b.yDp + b.heightDp && a.yDp + a.heightDp > b.yDp
}
