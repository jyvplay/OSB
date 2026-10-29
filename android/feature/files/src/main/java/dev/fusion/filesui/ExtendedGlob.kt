// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Clean-room ReDoS-immune extended glob & `.gitignore`-style path matcher (`ExtendedGlob`).
 *
 * Powers `:feature:files` batch selection and backup exclude filters:
 * - Supports `*` (any non-`/` chars), `?` (single non-`/` char), `**` (zero or more directory segments),
 *   character classes `[a-z0-9]` & negated `[!0-9]` / `[^0-9]`, and top-level brace expansion `{docx,xlsx,pdf}`!
 * - Uses memoized bottom-up dynamic programming over pattern tokens ($O(P \cdot S)$ worst-case time),
 *   guaranteeing zero exponential ReDoS backtracking on adversarial inputs like `a*a*a*a*a*b`!
 */
object ExtendedGlob {
    const val SCOPE =
        "Polynomial O(P*S) extended path glob matcher (*, ?, ** globstar, [a-z]/[!0-9] char classes, {a,b} brace expansion); POSIX collating symbols [=.=]] out of scope."

    const val MAX_BRACE_EXPANSIONS = 32

    fun matches(pattern: String, candidatePath: String): Boolean {
        val normPath = candidatePath.replace('\\', '/').trimStart('/')
        val expanded = expandBraces(pattern.replace('\\', '/').trimStart('/'))
        require(expanded.size <= MAX_BRACE_EXPANSIONS) {
            "Brace expansion count (${expanded.size}) exceeds MAX_BRACE_EXPANSIONS ($MAX_BRACE_EXPANSIONS)"
        }
        return expanded.any { matchSingleGlob(it, normPath) }
    }

    fun expandBraces(pattern: String): List<String> {
        val open = pattern.indexOf('{')
        if (open < 0) return listOf(pattern)
        var depth = 0
        var close = -1
        val parts = mutableListOf<String>()
        var segStart = open + 1
        for (i in open until pattern.length) {
            when (pattern[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        parts.add(pattern.substring(segStart, i))
                        close = i
                        break
                    }
                }
                ',' -> if (depth == 1) {
                    parts.add(pattern.substring(segStart, i))
                    segStart = i + 1
                }
            }
        }
        if (close < 0) return listOf(pattern)
        val prefix = pattern.substring(0, open)
        val suffix = pattern.substring(close + 1)
        return parts.flatMap { alt -> expandBraces(prefix + alt + suffix) }
    }

    private sealed interface PatTok {
        data object GlobStarSlash : PatTok // "**/": zero or more directory prefixes
        data object GlobStar : PatTok // "**": any char including '/'
        data object Star : PatTok // "*": any char except '/'
        data object AnyNonSlash : PatTok // "?"
        data class Literal(val ch: Char) : PatTok
        data class CharClass(val negated: Boolean, val ranges: List<CharRange>) : PatTok {
            fun matchesChar(ch: Char): Boolean {
                if (ch == '/') return false
                val hit = ranges.any { ch in it }
                return if (negated) !hit else hit
            }
        }
    }

    private fun matchSingleGlob(glob: String, path: String): Boolean {
        val toks = tokenize(glob)
        val m = toks.size
        val n = path.length
        // dp[i][j] = does toks[i..m) match path[j..n)?
        val dp = Array(m + 1) { BooleanArray(n + 1) }
        dp[m][n] = true

        for (i in m - 1 downTo 0) {
            val tok = toks[i]
            for (j in n downTo 0) {
                dp[i][j] = when (tok) {
                    PatTok.GlobStar -> dp[i + 1][j] || (j < n && dp[i][j + 1])
                    PatTok.GlobStarSlash -> {
                        // Matches zero directories (dp[i+1][j]) OR consumes through next '/' (path[j..k] == ".../")
                        dp[i + 1][j] || (j < n && (if (path[j] == '/') dp[i][j + 1] else dp[i][j + 1].takeIf { path.indexOf('/', j) >= 0 } ?: false))
                    }
                    PatTok.Star -> dp[i + 1][j] || (j < n && path[j] != '/' && dp[i][j + 1])
                    PatTok.AnyNonSlash -> j < n && path[j] != '/' && dp[i + 1][j + 1]
                    is PatTok.Literal -> j < n && path[j] == tok.ch && dp[i + 1][j + 1]
                    is PatTok.CharClass -> j < n && tok.matchesChar(path[j]) && dp[i + 1][j + 1]
                }
            }
            if (tok == PatTok.GlobStarSlash) {
                // Refine exact GlobStarSlash semantics: toks[i..] matches path[j..] iff toks[i+1..] matches path[j..]
                // OR there exists slashIdx >= j with path[slashIdx] == '/' such that toks[i..] matches path[slashIdx+1..]
                var suffixAfterSlashCanMatch = false
                for (j in n downTo 0) {
                    if (j < n && path[j] == '/' && dp[i + 1][j + 1]) {
                        suffixAfterSlashCanMatch = true
                    }
                    dp[i][j] = dp[i + 1][j] || suffixAfterSlashCanMatch
                }
            }
        }
        return dp[0][0]
    }

    private fun tokenize(glob: String): List<PatTok> {
        val out = mutableListOf<PatTok>()
        var i = 0
        while (i < glob.length) {
            when {
                glob.startsWith("**/", i) -> { out.add(PatTok.GlobStarSlash); i += 3 }
                glob.startsWith("**", i) -> { out.add(PatTok.GlobStar); i += 2 }
                glob[i] == '*' -> { out.add(PatTok.Star); i++ }
                glob[i] == '?' -> { out.add(PatTok.AnyNonSlash); i++ }
                glob[i] == '[' -> {
                    val close = glob.indexOf(']', i + 1)
                    if (close < 0) {
                        out.add(PatTok.Literal('['))
                        i++
                    } else {
                        var p = i + 1
                        var neg = false
                        if (p < close && (glob[p] == '!' || glob[p] == '^')) {
                            neg = true
                            p++
                        }
                        val ranges = mutableListOf<CharRange>()
                        while (p < close) {
                            if (p + 2 < close && glob[p + 1] == '-') {
                                ranges.add(glob[p]..glob[p + 2])
                                p += 3
                            } else {
                                ranges.add(glob[p]..glob[p])
                                p++
                            }
                        }
                        out.add(PatTok.CharClass(neg, ranges))
                        i = close + 1
                    }
                }
                else -> { out.add(PatTok.Literal(glob[i])); i++ }
            }
        }
        return out
    }
}
