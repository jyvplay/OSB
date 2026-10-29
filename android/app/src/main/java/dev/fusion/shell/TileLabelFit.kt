// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Clean-room launcher/tabletop tile label word-boundary fitter and bottom-dock slot budgeter.
 *
 * Repairs two concrete defects observed during visual inspection of `r24c-tabletop.png` and
 * `r24c-silver.png`:
 * 1. Mid-word single-line truncation (`"Tasks and ..."`, `"Settings an..."`) -> [fitLabel] wraps
 *    greedily on whitespace across `maxLines` (`2` by default), breaking multi-word titles cleanly
 *    onto line 2 (`"Tasks and"` / `"Automation"`, `"Settings and"` / `"Privacy"`) and using `\u2026`
 *    only when a word or 3rd-line tail genuinely exceeds the budget.
 * 2. Right-edge bottom-dock clipping of the 6th slot (`A11y`) on `390 dp` mobile viewports ->
 *    [planDock] enforces `minSlotDp >= 48` (WCAG 2.5.8 / Android 48dp touch floor) and reserves
 *    an explicit overflow slot (`hasOverflowButton = true`) whenever all items cannot fit cleanly.
 */
object TileLabelFit {
    const val SCOPE =
        "Monospace/character-budget 2-line word-boundary tile label fitter and >=48dp bottom-dock visible/overflow slot planner; proportional font glyph shaping lives in Compose TextMeasurer."

    const val ELLIPSIS = '\u2026'
    const val MIN_TOUCH_TARGET_DP = 48

    data class FittedLabel(
        val lines: List<String>,
        val truncated: Boolean,
    )

    data class DockPlan(
        val visibleItemCount: Int,
        val hiddenItemCount: Int,
        val hasOverflowButton: Boolean,
        val renderedSlots: Int,
        val slotWidthDp: Int,
    )

    fun fitLabel(rawTitle: String, maxCharsPerLine: Int, maxLines: Int = 2): FittedLabel {
        require(maxCharsPerLine >= 4) { "maxCharsPerLine $maxCharsPerLine must be >= 4" }
        require(maxLines in 1..4) { "maxLines $maxLines must be in 1..4" }
        val words = rawTitle.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return FittedLabel(listOf(""), false)

        val lines = ArrayList<String>(maxLines)
        var wIdx = 0
        var truncated = false

        while (wIdx < words.size && lines.size < maxLines) {
            val isLastAllowedLine = lines.size == maxLines - 1
            val firstWord = words[wIdx]
            if (firstWord.length > maxCharsPerLine) {
                lines.add(firstWord.substring(0, maxCharsPerLine - 1) + ELLIPSIS)
                truncated = true
                wIdx++
                if (isLastAllowedLine) break
                continue
            }
            var cur = firstWord
            wIdx++
            while (wIdx < words.size) {
                val candidate = "$cur ${words[wIdx]}"
                if (candidate.length <= maxCharsPerLine) {
                    cur = candidate
                    wIdx++
                } else {
                    break
                }
            }
            if (isLastAllowedLine && wIdx < words.size) {
                truncated = true
                cur = appendEllipsisWithinBudget(cur, maxCharsPerLine)
                lines.add(cur)
                break
            }
            lines.add(cur)
        }
        return FittedLabel(lines, truncated)
    }

    fun planDock(
        totalItems: Int,
        viewportWidthDp: Int,
        minSlotDp: Int = 56,
        horizontalPaddingDp: Int = 16,
        slotGapDp: Int = 6,
    ): DockPlan {
        require(totalItems >= 0) { "totalItems $totalItems must be >= 0" }
        require(viewportWidthDp >= 160) { "viewportWidthDp $viewportWidthDp must be >= 160" }
        require(minSlotDp >= MIN_TOUCH_TARGET_DP) {
            "minSlotDp $minSlotDp violates ${MIN_TOUCH_TARGET_DP}dp touch target floor"
        }
        require(horizontalPaddingDp >= 0 && slotGapDp >= 0) { "padding/gap must be non-negative" }
        if (totalItems == 0) return DockPlan(0, 0, false, 0, minSlotDp)

        val innerWidth = viewportWidthDp - 2 * horizontalPaddingDp
        require(innerWidth >= minSlotDp) { "usable dock width $innerWidth < minSlotDp $minSlotDp" }

        val maxSlots = ((innerWidth + slotGapDp) / (minSlotDp + slotGapDp)).coerceAtLeast(1)
        return if (totalItems <= maxSlots) {
            val slotW = (innerWidth - slotGapDp * (totalItems - 1)) / totalItems
            DockPlan(
                visibleItemCount = totalItems,
                hiddenItemCount = 0,
                hasOverflowButton = false,
                renderedSlots = totalItems,
                slotWidthDp = slotW,
            )
        } else {
            require(maxSlots >= 2) { "viewport $viewportWidthDp too narrow for item + overflow button" }
            val visible = maxSlots - 1
            val slotW = (innerWidth - slotGapDp * (maxSlots - 1)) / maxSlots
            DockPlan(
                visibleItemCount = visible,
                hiddenItemCount = totalItems - visible,
                hasOverflowButton = true,
                renderedSlots = maxSlots,
                slotWidthDp = slotW,
            )
        }
    }

    private fun appendEllipsisWithinBudget(line: String, budget: Int): String =
        if (line.length + 1 <= budget) {
            "$line$ELLIPSIS"
        } else {
            line.substring(0, budget - 1).trimEnd() + ELLIPSIS
        }
}
