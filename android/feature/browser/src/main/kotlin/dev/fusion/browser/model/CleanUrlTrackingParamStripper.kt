// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.net.URI

/**
 * Pure-JVM **Clean-URL Tracking Query-Parameter Sanitizer** (Brave / ClearURLs / Firefox ETP
 * clean-room parameter allow/deny stripper) for `:feature:browser` and Recall URL folder naming!
 *
 * Strips `utm_*`, `fbclid`, `gclid`, `msclkid`, `mc_eid`, `igshid`, `si`, `_hsenc`, `mkt_tok`,
 * and `yclid` while strictly preserving functional query parameters (`id`, `v`, `page`, `q`, `t`, `lang`).
 */
object CleanUrlTrackingParamStripper {
    const val SCOPE =
        "Browser & Recall Clean-URL tracking query-parameter stripper removing utm_*, fbclid, gclid, msclkid, igshid, and referral tokens while preserving functional parameters."

    private val TRACKING_EXACT = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "yclid", "twclid",
        "mc_eid", "igshid", "_hsenc", "_hsmi", "mkt_tok", "vero_id", "oly_enc_id",
    )

    data class CleanedUrlResult(
        val sanitizedUrl: String,
        val strippedParamNames: List<String>,
    )

    fun sanitize(rawUrl: String): CleanedUrlResult {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return CleanedUrlResult(rawUrl, emptyList())
        val rawQuery = uri.rawQuery ?: return CleanedUrlResult(rawUrl, emptyList())
        val kept = ArrayList<String>()
        val stripped = ArrayList<String>()

        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=').lowercase()
            val isTracker = key.startsWith("utm_") || key in TRACKING_EXACT
            if (isTracker) stripped.add(key) else kept.add(pair)
        }
        if (stripped.isEmpty()) return CleanedUrlResult(rawUrl, emptyList())

        val rebuilt = buildString {
            if (uri.scheme != null) append(uri.scheme).append("://")
            if (uri.rawAuthority != null) append(uri.rawAuthority)
            if (uri.rawPath != null) append(uri.rawPath)
            if (kept.isNotEmpty()) append('?').append(kept.joinToString("&"))
            if (uri.rawFragment != null) append('#').append(uri.rawFragment)
        }
        return CleanedUrlResult(rebuilt, stripped)
    }
}
