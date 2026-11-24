// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Bounded playback position tracker. Tracks playback position (in
 * milliseconds) for media items, bounded by item duration. Supports
 * position updates, seeking, and duration queries. Positions are clamped
 * to [0, duration]. Does not play media — the player supplies positions.
 *
 * Declared narrowings: at most [MAX_ITEMS] media items; item IDs at most
 * [MAX_ID_LEN] chars matching [ID_RE]; durations non-negative and at most
 * [MAX_DURATION_MS]; positions non-negative; negative positions refused.
 */
class PlaybackPositionTracker private constructor(
    private val positions: LinkedHashMap<String, PlaybackState>
) {
    companion object {
        const val MAX_ITEMS = 4096
        const val MAX_ID_LEN = 128
        const val MAX_DURATION_MS = 24L * 60 * 60 * 1000 // 24 hours
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")

        data class PlaybackState(
            var positionMs: Long,
            val durationMs: Long
        )

        fun empty(): PlaybackPositionTracker = PlaybackPositionTracker(LinkedHashMap())
    }

    fun setDuration(itemId: String, durationMs: Long) {
        require(ID_RE.matches(itemId)) { "invalid item id: $itemId" }
        require(itemId.length <= MAX_ID_LEN) { "item id too long" }
        require(durationMs >= 0 && durationMs <= MAX_DURATION_MS) { "duration out of bounds" }
        require(positions.size < MAX_ITEMS || positions.containsKey(itemId)) {
            "too many items: $MAX_ITEMS"
        }
        val current = positions[itemId]
        val pos = if (current != null) current.positionMs.coerceIn(0, durationMs.toInt()) else 0L
        positions[itemId] = PlaybackState(pos, durationMs)
    }

    fun updatePosition(itemId: String, positionMs: Long): Boolean {
        require(positionMs >= 0) { "negative position" }
        val state = positions[itemId] ?: return false
        state.positionMs = positionMs.coerceIn(0, state.durationMs.toInt())
        return true
    }

    fun seek(itemId: String, positionMs: Long): Boolean {
        require(positionMs >= 0) { "negative position" }
        val state = positions[itemId] ?: return false
        state.positionMs = positionMs.coerceIn(0, state.durationMs.toInt())
        return true
    }

    fun position(itemId: String): Long? = positions[itemId]?.positionMs
    fun duration(itemId: String): Long? = positions[itemId]?.durationMs
    fun itemCount(): Int = positions.size
    fun removeItem(itemId: String): Boolean = positions.remove(itemId) != null
    fun clear() = positions.clear()

    private fun Long.coerceIn(min: Int, max: Int): Long =
        if (this < min) min.toLong() else if (this > max) max.toLong() else this
}
