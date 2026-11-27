// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * R146 — deterministic no-repeat shuffle planner for the local playback queue
 * (privacy: the seed is device-local; no randomness source is consulted).
 *
 * Pipeline:
 *  1. seed the SplitMix64 PRNG with the first 8 bytes (big-endian) of
 *     SHA-256 over the UTF-8 seed string;
 *  2. Fisher–Yates shuffle: for i from n-1 down to 1, draw
 *     `j = next() % (i + 1)` and swap;
 *  3. re-deal with a no-repeat window: repeatedly take the first deck item
 *     that does not appear in the last [window] emitted items (scanning from
 *     the front); while at least `window + 1` items remain in the deck such
 *     an item always exists, so the guarantee is exact for that prefix. The
 *     final `window` items are emitted in deck order and may repeat within
 *     the window — an unavoidable tail property, stated rather than hidden.
 *
 * Declared narrowings: 2..[MAX_ITEMS] ids; ids `[A-Za-z0-9._-]{1,64}` and
 * unique; window in 1..[MAX_WINDOW]; seed 1..64 chars. Every draw is
 * unsigned (`next() and 0x7fff_ffff_ffff_ffffL`) so the modulo bias rule is
 * identical in Kotlin and any independent implementation.
 */
object QueueShufflePlanner {
    const val SCOPE =
        "SHA-256-seeded SplitMix64 Fisher-Yates shuffle plus windowed re-deal; fully deterministic, no runtime randomness."

    const val MAX_ITEMS = 1024
    const val MAX_WINDOW = 64
    const val MAX_ID = 64

    private val ID_RE = Regex("[A-Za-z0-9._-]{1,64}")

    /** SplitMix64 with the exact constants from the public reference. */
    class SplitMix64(seed: Long) {
        private var state = seed
        fun next(): Long {
            state += 0x9E3779B97F4A7C15UL.toLong()
            var z = state
            z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9UL.toLong()
            z = (z xor (z ushr 27)) * 0x94D049BB133111EBUL.toLong()
            return z xor (z ushr 31)
        }
    }

    fun plan(seed: String, window: Int, ids: List<String>): String {
        require(seed.length in 1..64) { "seed must be 1..64 chars" }
        require(window in 1..MAX_WINDOW) { "window out of 1..$MAX_WINDOW" }
        require(ids.size in 2..MAX_ITEMS) { "queue must have 2..$MAX_ITEMS items" }
        val seen = HashSet<String>()
        for (id in ids) {
            require(ID_RE.matches(id)) { "bad queue id: $id" }
            require(id.length <= MAX_ID) { "id too long" }
            require(seen.add(id)) { "duplicate queue id: $id" }
        }

        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        var seedLong = 0L
        for (b in 0 until 8) {
            seedLong = (seedLong shl 8) or (digest[b].toLong() and 0xff)
        }
        val rng = SplitMix64(seedLong)
        val deck = ids.toMutableList()
        for (i in deck.size - 1 downTo 1) {
            val j = (rng.next() and 0x7fff_ffff_ffff_ffffL) % (i + 1L)
            val tmp = deck[i]
            deck[i] = deck[j.toInt()]
            deck[j.toInt()] = tmp
        }

        // Windowed re-deal.
        val out = ArrayList<String>(deck.size)
        val remaining = ArrayDeque(deck)
        while (remaining.isNotEmpty()) {
            val tail = out.takeLast(window).toSet()
            val takeAt = if (out.size + remaining.size > window) {
                remaining.indexOfFirst { it !in tail }
            } else {
                0
            }
            val idx = if (takeAt >= 0) takeAt else 0
            out.add(remaining.removeAt(idx))
        }
        return out.joinToString(",")
    }
}
