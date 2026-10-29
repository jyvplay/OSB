// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

import kotlin.math.min

/**
 * Pure-JVM **Bottom-Right Stacked Front-to-Back Translucent Undo & Action Notification Deck**.
 *
 * Replaces full-width / right-side-hogging toast banners with a compact, perspective Z-stacked
 * translucent notification deck anchored strictly in the **bottom-right corner**:
 * - **Position**: Bottom-right (`marginEnd = 16 dp`, `marginBottom = 16 dp + dockClearanceDp`)
 * - **Compact Width Cap**: `cardWidthDp <= min(340.0, 0.32 * viewportWidthDp)` on desktop/tablet
 *   so notifications **never** take over the right side of the page (`totalStackHeight <= 0.26 * viewportHeightDp`)!
 * - **Front-to-Back Translucent Z-Stack (`maxVisibleCards = 3`)**:
 *   - `depth 0` (Front, newest): `alpha = 0.88` (translucent frosted surface), `scale = 1.00`, `offsetYDp = 0.0`
 *   - `depth 1` (Middle):        `alpha = 0.68`, `scale = 0.95`, `offsetYDp = -10.0` (peeks cleanly behind front)
 *   - `depth 2` (Back):          `alpha = 0.48`, `scale = 0.90`, `offsetYDp = -20.0` (peeks cleanly behind middle)
 *   - `> 3` items collapse into a `+N older` count pill on the back layer while remaining reachable in FIFO/LIFO history!
 */
object StackedUndoNotificationDeck {
    const val SCOPE =
        "Bottom-right front-to-back translucent Z-stacked Undo & action notification state machine (alpha 0.88/0.68/0.48, scale 1.00/0.95/0.90, -10dp peek, <=340dp / <=32% width cap)."

    const val MAX_VISIBLE_STACK_CARDS = 3
    const val MAX_CARD_WIDTH_DP = 340.0
    const val MIN_CARD_WIDTH_DP = 220.0
    const val MAX_VIEWPORT_WIDTH_FRACTION = 0.32
    const val CARD_HEIGHT_DP = 56.0
    const val PEEK_STEP_Y_DP = 10.0
    const val CORNER_MARGIN_DP = 16.0
    const val DEFAULT_TTL_MS = 8_000L

    private val LAYER_ALPHAS = doubleArrayOf(0.88, 0.68, 0.48)
    private val LAYER_SCALES = doubleArrayOf(1.00, 0.95, 0.90)

    data class ActionToastEntry(
        val id: String,
        val message: String,
        val undoLabel: String? = "Undo",
        val undoCommandId: String? = null,
        val createdAtMs: Long,
        val ttlMs: Long = DEFAULT_TTL_MS,
    )

    data class PositionedLayerCard(
        val entry: ActionToastEntry,
        val zDepthIndex: Int, // 0 = frontmost, 1 = middle, 2 = backmost
        val alpha: Double,
        val scale: Double,
        val translateYDp: Double, // negative = peeking upward behind front card
        val xDp: Double,
        val yDp: Double,
        val widthDp: Double,
        val heightDp: Double,
    )

    data class DeckLayoutPlan(
        val visibleFrontToBack: List<PositionedLayerCard>,
        val hiddenOlderCount: Int,
        val boundingBoxXDp: Double,
        val boundingBoxYDp: Double,
        val boundingBoxWidthDp: Double,
        val boundingBoxHeightDp: Double,
        val rightSideHeightFraction: Double,
    )

    class DeckState(
        val entriesNewestFirst: List<ActionToastEntry> = emptyList(),
        val maxRetainedEntries: Int = 24,
    ) {
        fun enqueue(entry: ActionToastEntry): DeckState {
            val deduped = entriesNewestFirst.filterNot { it.id == entry.id }
            return DeckState((listOf(entry) + deduped).take(maxRetainedEntries), maxRetainedEntries)
        }

        fun dismiss(id: String): DeckState =
            DeckState(entriesNewestFirst.filterNot { it.id == id }, maxRetainedEntries)

        fun popUndoTarget(id: String): Pair<DeckState, ActionToastEntry?> {
            val hit = entriesNewestFirst.firstOrNull { it.id == id } ?: return this to null
            return dismiss(id) to hit
        }

        fun pruneExpired(nowMs: Long): DeckState =
            DeckState(entriesNewestFirst.filter { nowMs - it.createdAtMs < it.ttlMs }, maxRetainedEntries)
    }

    fun computeBottomRightLayout(
        state: DeckState,
        viewportWidthDp: Double,
        viewportHeightDp: Double,
        bottomDockClearanceDp: Double = 64.0,
    ): DeckLayoutPlan {
        require(viewportWidthDp >= 240.0 && viewportHeightDp >= 240.0) { "Viewport must be >= 240x240dp" }
        val widthCap = if (viewportWidthDp >= 640.0) {
            min(MAX_CARD_WIDTH_DP, viewportWidthDp * MAX_VIEWPORT_WIDTH_FRACTION)
        } else {
            min(MAX_CARD_WIDTH_DP, viewportWidthDp - 2.0 * CORNER_MARGIN_DP)
        }
        val cardWidth = widthCap.coerceAtLeast(min(MIN_CARD_WIDTH_DP, widthCap))
        val anchorRightX = viewportWidthDp - CORNER_MARGIN_DP - cardWidth
        val frontCardTopY = (viewportHeightDp - bottomDockClearanceDp - CORNER_MARGIN_DP - CARD_HEIGHT_DP)
            .coerceAtLeast(CORNER_MARGIN_DP + 2.0 * PEEK_STEP_Y_DP)

        val visibleSlice = state.entriesNewestFirst.take(MAX_VISIBLE_STACK_CARDS)
        val hiddenCount = (state.entriesNewestFirst.size - visibleSlice.size).coerceAtLeast(0)

        val cards = visibleSlice.mapIndexed { depth, entry ->
            val alpha = LAYER_ALPHAS[depth]
            val scale = LAYER_SCALES[depth]
            val dy = -depth * PEEK_STEP_Y_DP
            val scaledW = cardWidth * scale
            val xCenteredInStack = anchorRightX + (cardWidth - scaledW) / 2.0
            val yTop = frontCardTopY + dy
            PositionedLayerCard(
                entry = entry,
                zDepthIndex = depth,
                alpha = alpha,
                scale = scale,
                translateYDp = dy,
                xDp = xCenteredInStack,
                yDp = yTop,
                widthDp = scaledW,
                heightDp = CARD_HEIGHT_DP * scale,
            )
        }

        val peekSpan = if (cards.isEmpty()) 0.0 else (cards.size - 1) * PEEK_STEP_Y_DP
        val totalStackHeight = if (cards.isEmpty()) 0.0 else CARD_HEIGHT_DP + peekSpan
        val bboxY = if (cards.isEmpty()) frontCardTopY else frontCardTopY - peekSpan
        return DeckLayoutPlan(
            visibleFrontToBack = cards,
            hiddenOlderCount = hiddenCount,
            boundingBoxXDp = anchorRightX,
            boundingBoxYDp = bboxY,
            boundingBoxWidthDp = cardWidth,
            boundingBoxHeightDp = totalStackHeight,
            rightSideHeightFraction = totalStackHeight / viewportHeightDp,
        )
    }
}

/** Emits a ready-to-enqueue bottom-right translucent Z-stack Undo notification card from any UndoRedoStack. */
fun <S> UndoRedoStack<S>.toBottomRightToastEntry(
    id: String,
    summaryMessage: String,
    nowMs: Long,
): StackedUndoNotificationDeck.ActionToastEntry {
    val receipt = createUndoReceipt(id, summaryMessage, nowMs)
    return StackedUndoNotificationDeck.ActionToastEntry(
        id = receipt.id,
        message = receipt.message,
        undoLabel = if (receipt.undoAvailable) "Undo" else null,
        undoCommandId = receipt.undoCommandId,
        createdAtMs = receipt.createdAtMs,
    )
}
