package dev.fusion.find

/**
 * Shell-style glob matching for the find surface, fnmatch-compatible.
 *
 * Semantics follow POSIX fnmatch(3) without FNM_PATHNAME, exactly as
 * Python's `fnmatch.fnmatchcase` implements them (that implementation is
 * this kernel's held-out cross-verifier): `*` matches any run including
 * empty, `?` matches exactly one character, `[seq]` a set with ranges,
 * `[!seq]` its complement; a `]` first in the set is a literal; an
 * unterminated `[` is a literal bracket. Matching is case-sensitive and
 * anchored at both ends.
 *
 * Declared narrowings: no `**`, no path-segment awareness, no locale
 * collation inside ranges (code-point order), no escaping backslash
 * (fnmatch has none either — use `[*]` to match a literal star).
 */
object GlobMatch {
    const val SCOPE = "Anchored fnmatch-without-FNM_PATHNAME semantics; case-sensitive; no ** or backslash escapes."

    private data class SetSpec(val negated: Boolean, val chars: Set<Char>, val end: Int)

    /** Parse a bracket set starting at `[`; returns null when unterminated (literal bracket). */
    private fun parseSet(p: String, start: Int): SetSpec? {
        var i = start + 1
        val negated = i < p.length && p[i] == '!'
        if (negated) i++
        val first = i
        val chars = HashSet<Char>()
        while (i < p.length) {
            val c = p[i]
            if (c == ']' && i > first) return SetSpec(negated, chars, i)
            if (i + 2 < p.length && p[i + 1] == '-' && p[i + 2] != ']') {
                val lo = p[i]; val hi = p[i + 2]
                require(lo <= hi) { "reversed range $lo-$hi refused (fnmatch raises on these too)" }
                var ch = lo
                while (ch <= hi) { chars.add(ch); ch++ }
                i += 3
            } else { chars.add(c); i++ }
        }
        return null
    }

    fun matches(pattern: String, text: String): Boolean {
        var p = 0; var t = 0
        var starP = -1; var starT = -1
        while (t < text.length) {
            if (p < pattern.length) {
                when (val pc = pattern[p]) {
                    '*' -> { starP = p; starT = t; p++; continue }
                    '?' -> { p++; t++; continue }
                    '[' -> {
                        val set = parseSet(pattern, p)
                        if (set == null) {
                            if (text[t] == '[') { p++; t++; continue }
                        } else {
                            val inSet = text[t] in set.chars
                            if (inSet != set.negated) { p = set.end + 1; t++; continue }
                        }
                    }
                    else -> if (pc == text[t]) { p++; t++; continue }
                }
            }
            if (starP >= 0) { starT++; t = starT; p = starP + 1 } else return false
        }
        while (p < pattern.length && pattern[p] == '*') p++
        return p == pattern.length
    }
}
