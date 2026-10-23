// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Tracking-query stripping for share URLs.
 *
 * Sits next to [LogScrubber] (which redacts PII shapes in free text)
 * and [FieldMask] (which drops whole record fields): neither cleans
 * the `utm_*`/`fbclid`/`gclid` parameters pasted links carry. [clean]
 * drops every query parameter whose name (case-insensitively) is in
 * the documented tracking set, keeps the rest byte-for-byte in order,
 * and preserves the fragment. An emptied query drops the `?`. The set
 * is a denylist of KNOWN trackers — unknown parameters are kept, so a
 * cleaned URL is "less tracked", never "untracked".
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object UtmStrip {
    const val MAX_URL = 8_000

    /** Known tracking parameter names (matched case-insensitively). */
    val TRACKERS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid",
        "igshid", "ttclid", "twclid", "yclid", "_openstat", "vero_id",
    )

    /**
     * Returns [url] with tracking query parameters removed.
     *
     * @throws IllegalArgumentException on a blank or over-long URL.
     */
    fun clean(url: String): String {
        require(url.isNotBlank()) { "blank url" }
        require(url.length <= MAX_URL) { "url over cap" }
        require('\n' !in url && '\r' !in url) { "bad url" }
        val hash = url.indexOf('#')
        val frag = if (hash >= 0) url.substring(hash) else ""
        val head = if (hash >= 0) url.substring(0, hash) else url
        val q = head.indexOf('?')
        if (q < 0) return url
        val base = head.substring(0, q)
        val kept = head.substring(q + 1).split("&").filter { pair ->
            val name = pair.substringBefore("=").lowercase()
            name.isNotEmpty() && name !in TRACKERS
        }
        if (kept.isEmpty()) return base + frag
        return base + "?" + kept.joinToString("&") + frag
    }
}
