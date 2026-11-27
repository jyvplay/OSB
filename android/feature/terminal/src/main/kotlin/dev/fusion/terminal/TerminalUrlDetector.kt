// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * R146 — privacy-minded URL detector for terminal scrollback.
 *
 * Finds `http://`, `https://`, `ftp://` and `www.` URLs in bounded text,
 * trims trailing punctuation that is rarely part of the URL, and rewrites
 * embedded `userinfo@` credentials to `!CRED!` so a password pasted into a
 * link can never be echoed back by a "open link" affordance.
 *
 * Rules (deterministic, left-to-right, non-overlapping):
 *  - a match starts at the scheme (case-insensitive) or at `www.`, and never
 *    in the middle of a word (no alphanumeric immediately before);
 *  - the authority runs to the first `/?#` or match end and must contain a
 *    dot, with no leading/trailing/double dots, `[A-Za-z0-9.-]` host charset,
 *    and an optional `:port` of 1..5 digits <= 65535;
 *  - `userinfo@` may precede the host (last `@` in the authority separates);
 *  - the match ends at whitespace, `<`, `>` or `"`, then trailing
 *    `. , ; : ! ? ) ] } ' "` characters are stripped;
 *  - at most [MAX_MATCHES] matches, URL length <= [MAX_URL],
 *    input <= [MAX_CHARS] UTF-16 units — each bound refuses rather than
 *    truncates.
 *
 * Output: `offset:url` pairs joined by `,` where the offset counts UTF-16
 * units from the start of the original input and the url is the trimmed,
 * credential-redacted match.
 */
object TerminalUrlDetector {
    const val SCOPE =
        "Bounded left-to-right URL scan with credential redaction and match cap; no DNS, no fetching."

    const val MAX_CHARS = 65_536
    const val MAX_MATCHES = 256
    const val MAX_URL = 2048

    private val SCHEMES = listOf("http://", "https://", "ftp://")
    private const val WWW = "www."
    private val TRAILING = ".,;:!?)]}'\""
    private val STOP = charArrayOf(' ', '\t', '\n', '\r', '<', '>', '"')
    private val HOST_CHARSET = Regex("[A-Za-z0-9.-]+")

    fun find(text: String): String {
        require(text.length <= MAX_CHARS) { "input over $MAX_CHARS chars" }
        val hits = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val schemeLen = SCHEMES.firstOrNull {
                text.regionMatches(i, it, 0, it.length, ignoreCase = true)
            }?.length
            val wwwHere = schemeLen == null && text.regionMatches(i, WWW, 0, 4, ignoreCase = true)
            if (schemeLen == null && !wwwHere) {
                i++
                continue
            }
            val start = i
            // A match may not start in the middle of a word: the character
            // before the scheme/www must not be alphanumeric.
            if (start > 0 && text[start - 1].isLetterOrDigit()) {
                i++
                continue
            }
            val authStart = i + (schemeLen ?: 0)
            val end = scanEnd(text, authStart) ?: run { i++; continue }
            val candidate = text.substring(start, end)
            if (candidate.length > MAX_URL) throw IllegalArgumentException("URL over $MAX_URL chars")
            // A candidate that fails URL-shape validation is skipped, not
            // treated as a refusal: stray "www." text must not disable
            // detection for the rest of the scrollback. The hard bounds
            // (length, count) above and below still refuse the whole call.
            if (!valid(text, start, authStart, end)) {
                i = start + 1
                continue
            }
            val redacted = redactCredentials(text, start, authStart, end)
            require(hits.size < MAX_MATCHES) { "more than $MAX_MATCHES URLs" }
            hits.add("$start:$redacted")
            i = end
        }
        return hits.joinToString(",")
    }

    /** First index after the match, before trailing-punctuation trim. */
    private fun scanEnd(text: String, from: Int): Int? {
        var k = from
        while (k < text.length && text[k] !in STOP) k++
        if (k == from) return null
        var end = k
        while (end > from && text[end - 1] in TRAILING) end--
        return if (end > from) end else null
    }

    private fun authorityOf(text: String, authStart: Int, end: Int): String {
        val cut = (authStart until end).firstOrNull { text[it] in "/?#" } ?: end
        return text.substring(authStart, cut)
    }

    /** Returns false instead of throwing: callers skip invalid candidates. */
    private fun valid(text: String, start: Int, authStart: Int, end: Int): Boolean {
        if (end <= start) return false
        val authority = authorityOf(text, authStart, end)
        if (authority.isEmpty()) return false
        val sep = authority.lastIndexOf('@')
        val hostPort = if (sep >= 0) {
            if (sep == 0) return false
            authority.substring(sep + 1)
        } else authority
        if (hostPort.isEmpty()) return false
        val colon = hostPort.indexOf(':')
        val host: String
        if (colon >= 0) {
            if (colon != hostPort.lastIndexOf(':')) return false
            host = hostPort.substring(0, colon)
            val port = hostPort.substring(colon + 1)
            if (port.length !in 1..5) return false
            if (!port.all { it in '0'..'9' }) return false
            if (port.toInt() > 65535) return false
        } else {
            host = hostPort
        }
        if ('.' !in host) return false
        if (!HOST_CHARSET.matches(host)) return false
        if (host.startsWith(".") || host.endsWith(".") || host.contains("..")) return false
        if (authority.any { it in STOP || it == '[' || it == ']' }) return false
        return true
    }

    private fun redactCredentials(text: String, start: Int, authStart: Int, end: Int): String {
        val authority = authorityOf(text, authStart, end)
        val sep = authority.lastIndexOf('@')
        val prefix = text.substring(start, authStart)
        val suffix = text.substring(authStart + authority.length, end)
        return if (sep >= 0) {
            prefix + "!CRED!" + authority.substring(sep) + suffix
        } else {
            text.substring(start, end)
        }
    }
}
