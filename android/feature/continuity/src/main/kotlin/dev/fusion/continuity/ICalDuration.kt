// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * RFC 5545 duration values to whole seconds: `[+|-]P(nW | nD[TnHnMnS] |
 * TnHnMnS)`. Weeks stand alone; time parts keep H-M-S order and appear at
 * most once. Calendrical months/years are refused (their length depends on
 * the start date — no DST-style guessing here). Caps: 9 digits per part,
 * 10 years total.
 */
object ICalDuration {
    const val MAX_SECONDS = 315_532_800L // 10 leap-aware years

    fun toSeconds(text: String): Long {
        require(text.isNotEmpty()) { "empty duration" }
        var i = 0
        var sign = 1L
        if (text[i] == '+' || text[i] == '-') {
            if (text[i] == '-') sign = -1
            i++
        }
        require(i < text.length && text[i] == 'P') { "missing P" }
        i++
        require(i < text.length) { "bare P" }
        var seconds = 0L
        var seenDate = false
        var seenTime = false
        var seenT = false
        fun digits(): Long {
            val start = i
            while (i < text.length && text[i].isDigit()) i++
            require(i - start in 1..9) { "bad number at $start" }
            return text.substring(start, i).toLong()
        }
        if (text[i] != 'T') {
            val n = digits()
            require(i < text.length) { "trailing number" }
            when (text[i]) {
                'W' -> {
                    i++
                    require(i == text.length) { "weeks must stand alone" }
                    seconds = Math.multiplyExact(n, 604_800L)
                    return checked(sign, seconds)
                }
                'D' -> {
                    i++
                    seenDate = true
                    seconds = Math.multiplyExact(n, 86_400L)
                }
                else -> throw IllegalArgumentException("bad date designator ${text[i]}")
            }
        }
        if (i < text.length && text[i] == 'T') {
            seenT = true
            i++
            require(i < text.length) { "bare T" }
            var lastOrder = 0
            while (i < text.length) {
                val n = digits()
                require(i < text.length) { "trailing number" }
                val order = when (text[i]) {
                    'H' -> 3
                    'M' -> 2
                    'S' -> 1
                    else -> throw IllegalArgumentException("bad time designator ${text[i]}")
                }
                require(order < lastOrder || lastOrder == 0) { "time parts out of order" }
                lastOrder = order
                seconds = Math.addExact(seconds, Math.multiplyExact(n, when (order) { 3 -> 3_600L; 2 -> 60L; else -> 1L }))
                seenTime = true
                i++
            }
        }
        require(i == text.length) { "trailing input at $i" }
        require(seenDate || seenTime) { "empty duration body" }
        require(!seenT || seenDate || seenTime) { "bare T" }
        return checked(sign, seconds)
    }

    private fun checked(sign: Long, seconds: Long): Long {
        require(seconds in 0..MAX_SECONDS) { "over $MAX_SECONDS seconds" }
        return sign * seconds
    }
}
