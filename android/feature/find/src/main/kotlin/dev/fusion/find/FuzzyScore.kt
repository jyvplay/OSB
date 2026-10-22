// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Subsequence fuzzy score for palette ranking: pattern chars must appear
 * in order (case-insensitive) or the result is null. Greedy leftmost
 * alignment (documented — not optimal). +10 per char, +15 at a word
 * start (position 0 or after a non-alphanumeric), +8 for a camel-hump
 * (target uppercase after lowercase), +5 per adjacency after the first
 * char, −1 per interior skipped char (leading skips free). Empty pattern
 * scores 0. Caps: 256 chars each.
 */
object FuzzyScore {
    const val MAX_LEN = 256

    fun score(pattern: String, target: String): Int? {
        require(pattern.length <= MAX_LEN && target.length <= MAX_LEN) { "over $MAX_LEN chars" }
        if (pattern.isEmpty()) return 0
        var total = 0
        var ti = 0
        var prev = -2
        var first = -1
        for (pc in pattern) {
            var hit = -1
            var j = ti
            while (j < target.length) {
                if (target[j].lowercaseChar() == pc.lowercaseChar()) {
                    hit = j; break
                }
                j++
            }
            if (hit < 0) return null
            if (first < 0) first = hit
            total += 10
            if (hit == 0 || !target[hit - 1].isLetterOrDigit()) total += 15
            if (hit > 0 && target[hit].isUpperCase() && target[hit - 1].isLowerCase()) total += 8
            if (hit == prev + 1) total += 5
            if (prev >= 0) total -= (hit - prev - 1)
            prev = hit
            ti = hit + 1
        }
        return total
    }
}
