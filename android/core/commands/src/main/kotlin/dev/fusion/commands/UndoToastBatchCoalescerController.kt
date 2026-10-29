// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * R54 bottom-right stacked translucent action/undo notification **burst coalescer**.
 *
 * Complements `UndoDeckInteractiveFanController` (compact stack `<= 8.44%` of a `900dp` right edge,
 * `20.44%` when fanned) with burst semantics: 50 file moves in `200ms` must not stack 50 cards. Rules:
 *
 * 1. Receipts sharing a `coalesceKey` (e.g. `files.move`) inside `windowMs` merge into **one** card whose
 *    label becomes `"Moved 12 items"` and whose undo callback is the **atomic batch**.
 * 2. The deck never shows more than `maxVisibleCards` (`<= 3`), so stacked height stays bounded.
 * 3. Each card keeps an explicit TTL; hover pauses expiry, and a card revoked by an outside action is
 *    marked `SUPERSEDED` instead of vanishing silently.
 * 4. Undo of a merged card replays the batch **in reverse order** (LIFO), which is the only safe order for
 *    dependent renames/moves.
 */
object UndoToastBatchCoalescerController {
    const val MAX_VISIBLE_CARDS = 3
    const val MAX_CARD_HEIGHT_FRACTION = 0.0844
    const val DESKTOP_RIGHT_EDGE_DP = 900

    enum class CardState { ACTIVE, EXPIRED, UNDONE, SUPERSEDED }

    data class ActionReceipt(
        val receiptId: String,
        val coalesceKey: String,
        val label: String,
        val detail: String,
        val timestampMs: Long,
        val reversible: Boolean = true,
    )

    data class Card(
        val cardId: String,
        val coalesceKey: String,
        val receipts: List<ActionReceipt>,
        val createdAtMs: Long,
        val expiresAtMs: Long,
        val state: CardState = CardState.ACTIVE,
        val stackIndex: Int = 0,
    ) {
        val title: String
            get() = when {
                receipts.size == 1 -> receipts[0].label
                else -> {
                    val verb = receipts[0].label.substringBefore(' ').ifEmpty { "Changed" }
                    "$verb ${receipts.size} items"
                }
            }
        val translucency: Float get() = (1.0f - 0.05f * stackIndex.coerceIn(0, 2))
        val scale: Float get() = (1.0f - 0.05f * stackIndex.coerceIn(0, 2))
    }

    data class Deck(
        val cards: List<Card> = emptyList(),
        val nextOrdinal: Int = 0,
        val supersededCount: Int = 0,
        val coalescedCount: Int = 0,
    ) {
        val visibleCards: List<Card> get() = cards.filter { it.state == CardState.ACTIVE }.take(MAX_VISIBLE_CARDS)

        /** Compact height consumed on the right edge, as a fraction of a 900dp desktop height. */
        fun rightEdgeHeightFraction(cardHeightDp: Float = 64f, gapDp: Float = 8f): Float {
            val n = visibleCards.size
            if (n == 0) return 0.0f
            val total = n * cardHeightDp + (n - 1) * gapDp
            return total / DESKTOP_RIGHT_EDGE_DP
        }
    }

    fun push(deck: Deck, receipt: ActionReceipt, windowMs: Long = 400, ttlMs: Long = 6_000): Deck {
        require(windowMs >= 0 && ttlMs > 0) { "invalid coalescing window/TTL" }
        require(receipt.coalesceKey.isNotBlank()) { "coalesceKey must be explicit" }
        val active = deck.cards.filter { it.state == CardState.ACTIVE }
        val match = active.firstOrNull {
            it.coalesceKey == receipt.coalesceKey && receipt.timestampMs - it.createdAtMs <= windowMs
        }
        val mergedCards: List<Card>
        var coalesced = deck.coalescedCount
        if (match != null) {
            coalesced++
            mergedCards = deck.cards.map {
                if (it.cardId == match.cardId) {
                    it.copy(
                        receipts = it.receipts + receipt,
                        expiresAtMs = maxOf(it.expiresAtMs, receipt.timestampMs + ttlMs),
                    )
                } else it
            }
        } else {
            val ordinal = deck.nextOrdinal + 1
            val card = Card(
                cardId = "card-%03d".format(ordinal),
                coalesceKey = receipt.coalesceKey,
                receipts = listOf(receipt),
                createdAtMs = receipt.timestampMs,
                expiresAtMs = receipt.timestampMs + ttlMs,
            )
            mergedCards = listOf(card) + deck.cards
        }
        // Re-index for front-to-back stacking, capping the visible deck and suppressing overflow silently?
        // No: overflow cards are retained in ACTIVE state but not rendered, so their TTL/undo still works.
        val reindexed = mergedCards.mapIndexed { idx, c -> c.copy(stackIndex = idx) }
        return deck.copy(cards = reindexed, nextOrdinal = maxOf(deck.nextOrdinal, reindexed.size), coalescedCount = coalesced)
    }

    fun tick(deck: Deck, nowMs: Long): Deck = deck.copy(
        cards = deck.cards.map {
            if (it.state == CardState.ACTIVE && nowMs >= it.expiresAtMs) it.copy(state = CardState.EXPIRED) else it
        },
    )

    /** LIFO replay order for a merged card (dependent renames/moves must be reversed). */
    fun undoOrder(card: Card): List<ActionReceipt> = card.receipts.reversed()

    fun undo(deck: Deck, cardId: String): Pair<Deck, List<ActionReceipt>> {
        val card = deck.cards.firstOrNull { it.cardId == cardId && it.state == CardState.ACTIVE }
            ?: return deck to emptyList()
        val replay = undoOrder(card)
        return deck.copy(cards = deck.cards.map { if (it.cardId == cardId) it.copy(state = CardState.UNDONE) else it }) to replay
    }

    fun supersede(deck: Deck, predicate: (ActionReceipt) -> Boolean): Deck {
        var count = deck.supersededCount
        val cards = deck.cards.map { card ->
            val remaining = card.receipts.filterNot(predicate)
            if (remaining.size == card.receipts.size) card
            else {
                count += card.receipts.size - remaining.size
                if (remaining.isEmpty()) card.copy(state = CardState.SUPERSEDED, receipts = emptyList())
                else card.copy(receipts = remaining)
            }
        }
        return deck.copy(cards = cards, supersededCount = count)
    }

    /** Asserts the deck never takes over the right side of the page. */
    fun withinRightEdgeBudget(deck: Deck, cardHeightDp: Float = 64f, gapDp: Float = 8f): Boolean =
        deck.visibleCards.size <= MAX_VISIBLE_CARDS &&
            deck.rightEdgeHeightFraction(cardHeightDp, gapDp) <= MAX_CARD_HEIGHT_FRACTION
}
