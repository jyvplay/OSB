// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Glob subset over file NAMES (not paths — `/` has no special meaning):
 * `*` (any run), `?` (one char), `[...]` (chars and `a-z` ranges,
 * `[!...]` negation, `]` first and `-` first/last are literal),
 * backslash escapes the next char. Case-sensitive, full-match. `**` is
 * just two stars (no recursive meaning). Unmatched DP over tokens ×
 * chars; no exponential backtracking. Caps: 256-char patterns, 1 KiB names.
 */
object GlobMatch {
    const val MAX_PATTERN = 256
    const val MAX_NAME = 1024

    private sealed interface Tok {
        data object Star : Tok
        data object Any1 : Tok
        data class Lit(val c: Char) : Tok
        data class Cls(val chars: Set<Char>, val neg: Boolean) : Tok
    }

    fun match(pattern: String, name: String): Boolean {
        require(pattern.length <= MAX_PATTERN) { "pattern over $MAX_PATTERN chars" }
        require(name.length <= MAX_NAME) { "name over $MAX_NAME chars" }
        val toks = tokenize(pattern)
        var cur = BooleanArray(name.length + 1)
        cur[0] = true
        for (t in toks) {
            val next = BooleanArray(name.length + 1)
            when (t) {
                is Tok.Star -> {
                    var seen = false
                    for (j in 0..name.length) {
                        seen = seen || cur[j]
                        next[j] = seen
                    }
                }
                is Tok.Any1 -> for (j in 1..name.length) next[j] = cur[j - 1]
                is Tok.Lit -> for (j in 1..name.length) next[j] = cur[j - 1] && name[j - 1] == t.c
                is Tok.Cls -> for (j in 1..name.length) {
                    next[j] = cur[j - 1] && (t.chars.contains(name[j - 1]) != t.neg)
                }
            }
            cur = next
        }
        return cur[name.length]
    }

    private fun tokenize(p: String): List<Tok> {
        val toks = mutableListOf<Tok>()
        var i = 0
        while (i < p.length) {
            when (val c = p[i]) {
                '*' -> { toks += Tok.Star; i++ }
                '?' -> { toks += Tok.Any1; i++ }
                '\\' -> {
                    require(i + 1 < p.length) { "trailing backslash" }
                    toks += Tok.Lit(p[i + 1]); i += 2
                }
                '[' -> {
                    var j = i + 1
                    val neg = j < p.length && p[j] == '!'
                    if (neg) j++
                    val chars = mutableSetOf<Char>()
                    if (j < p.length && p[j] == ']') {
                        chars += ']'; j++
                    }
                    var closed = false
                    while (j < p.length) {
                        if (p[j] == ']') {
                            closed = true; j++; break
                        }
                        if (j + 2 < p.length && p[j + 1] == '-' && p[j + 2] != ']') {
                            val lo = p[j]
                            val hi = p[j + 2]
                            require(lo <= hi) { "reversed range $lo-$hi" }
                            for (k in lo..hi) chars += k
                            j += 3
                        } else {
                            chars += p[j]; j++
                        }
                    }
                    require(closed) { "unterminated class" }
                    toks += Tok.Cls(chars, neg)
                    i = j
                }
                else -> { toks += Tok.Lit(c); i++ }
            }
        }
        return toks
    }
}
