// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * **Interactive Compact Z-Stack <-> Vertical Fan-Out Controller** for the Bottom-Right Translucent
 * Undo & Action Notification Deck (`StackedUndoNotificationDeck`).
 *
 * - **Compact Resting Mode (Default)**: Front-to-back perspective Z-stack (`-10 dp` peek,
 *   `alpha = 0.88 / 0.68 / 0.48`, `scale = 1.00 / 0.95 / 0.90`, `< 9%` of desktop height) so
 *   notifications never take over the right side of the page!
 * - **Hovered / Tapped Fan-Out Mode**: Smoothly separates the top `<= 3` cards vertically (`8 dp` gap,
 *   `scale = 1.00`, `alpha = 0.92`) while **freezing TTL expiration** during pointer hover so the
 *   user can click `Undo` on any individual action without the card vanishing under their cursor!
 */
object UndoDeckInteractiveFanController {
    const val SCOPE =
        "Bottom-right translucent Undo Z-stack compact (<9% height) <-> hover-fanned state controller with TTL freeze and live progress fraction."

    data class FannedCardSnapshot(
        val id: String,
        val message: String,
        val zDepthIndex: Int,
        val xDp: Double,
        val yDp: Double,
        val widthDp: Double,
        val heightDp: Double,
        val alpha: Double,
        val scale: Double,
        val remainingTtlFraction: Double,
    )

    data class InteractiveDeckFrame(
        val expandedFanActive: Boolean,
        val ttlPausedByHover: Boolean,
        val cardsFrontToBack: List<FannedCardSnapshot>,
        val compactRightSideHeightFraction: Double,
        val expandedRightSideHeightFraction: Double,
    )

    fun computeFrame(
        state: StackedUndoNotificationDeck.DeckState,
        expandedByUserHoverOrTap: Boolean,
        nowMillis: Long,
        viewportWidthDp: Double,
        viewportHeightDp: Double,
        dockClearanceDp: Double = 48.0,
    ): InteractiveDeckFrame {
        val compact = StackedUndoNotificationDeck.computeBottomRightLayout(
            state = state,
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            bottomDockClearanceDp = dockClearanceDp,
        )
        if (compact.visibleFrontToBack.isEmpty()) {
            return InteractiveDeckFrame(expandedByUserHoverOrTap, expandedByUserHoverOrTap, emptyList(), 0.0, 0.0)
        }

        val cards = compact.visibleFrontToBack.map { c ->
            val elapsed = (nowMillis - c.entry.createdAtMs).coerceAtLeast(0L)
            val ttlFrac = if (expandedByUserHoverOrTap) {
                (1.0 - elapsed.toDouble() / c.entry.ttlMs.coerceAtLeast(1L)).coerceIn(0.15, 1.0)
            } else {
                (1.0 - elapsed.toDouble() / c.entry.ttlMs.coerceAtLeast(1L)).coerceIn(0.0, 1.0)
            }
            if (!expandedByUserHoverOrTap) {
                FannedCardSnapshot(
                    id = c.entry.id,
                    message = c.entry.message,
                    zDepthIndex = c.zDepthIndex,
                    xDp = c.xDp,
                    yDp = c.yDp,
                    widthDp = c.widthDp,
                    heightDp = c.heightDp,
                    alpha = c.alpha,
                    scale = c.scale,
                    remainingTtlFraction = ttlFrac,
                )
            } else {
                // Fanned upward from bottom-right anchor with 8dp vertical gap; capped to <= 3 cards (< 20% height)
                val step = StackedUndoNotificationDeck.CARD_HEIGHT_DP + 8.0
                val fanY = compact.visibleFrontToBack[0].yDp - c.zDepthIndex * step
                FannedCardSnapshot(
                    id = c.entry.id,
                    message = c.entry.message,
                    zDepthIndex = c.zDepthIndex,
                    xDp = compact.visibleFrontToBack[0].xDp,
                    yDp = fanY.coerceAtLeast(16.0),
                    widthDp = compact.visibleFrontToBack[0].widthDp,
                    heightDp = c.heightDp,
                    alpha = (0.92 - c.zDepthIndex * 0.04).coerceIn(0.80, 0.94),
                    scale = 1.00,
                    remainingTtlFraction = ttlFrac,
                )
            }
        }
        val topY = cards.minOf { it.yDp }
        val botY = cards.maxOf { it.yDp + it.heightDp }
        val expandedFrac = (botY - topY) / viewportHeightDp.coerceAtLeast(1.0)
        return InteractiveDeckFrame(
            expandedFanActive = expandedByUserHoverOrTap,
            ttlPausedByHover = expandedByUserHoverOrTap,
            cardsFrontToBack = cards,
            compactRightSideHeightFraction = compact.rightSideHeightFraction,
            expandedRightSideHeightFraction = expandedFrac,
        )
    }
}
