// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Clean-room ClearURLs-Grade Tracking Parameter Stripper & Outbound Redirect Unwrapper.
 *
 * Strips cross-site click identifiers (`fbclid`, `gclid`, `dclid`, `msclkid`, `mc_eid`, `igshid`,
 * `twclid`, `yclid`, `utm_*`, `_hsenc`, `mkt_tok`) and unwraps tracking bounce URLs while keeping
 * functional query keys and fragments intact.
 */
object ClearUrlTrackingParamStripper {
    const val SCOPE =
        "Pure-JVM ClearURLs tracking query-parameter stripper and redirect-bounce unwrapper preserving legitimate functional parameters."

    private val EXACT_TRACKERS = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "mc_eid",
        "igshid", "twclid", "yclid", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id",
    )

    data class CleanUrlResult(
        val cleanedUrl: String,
        val strippedParams: List<String>,
        val unwrappedRedirect: Boolean,
    )

    fun sanitizeUrl(rawUrl: String): CleanUrlResult {
        val uri = URI(rawUrl.trim())
        require(uri.scheme?.lowercase() in setOf("https", "http")) { "expected http(s) URL" }

        // Check known outbound redirect wrappers (?url=https%3A... or ?u=https%3A...)
        val rawQuery = uri.rawQuery
        if (rawQuery != null && (uri.host?.contains("l.facebook.com") == true || uri.path?.endsWith("/redirect") == true)) {
            for (pair in rawQuery.split('&')) {
                val kv = pair.split('=', limit = 2)
                if (kv.size == 2 && kv[0] in setOf("u", "url", "q", "target")) {
                    val decoded = URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                    if (decoded.startsWith("https://") || decoded.startsWith("http://")) {
                        val inner = sanitizeUrl(decoded)
                        return inner.copy(unwrappedRedirect = true)
                    }
                }
            }
        }

        if (rawQuery.isNullOrEmpty()) return CleanUrlResult(uri.toASCIIString(), emptyList(), false)

        val kept = ArrayList<String>()
        val removed = ArrayList<String>()
        for (pair in rawQuery.split('&').filter { it.isNotEmpty() }) {
            val key = pair.substringBefore('=').lowercase()
            val isTracker = key in EXACT_TRACKERS || key.startsWith("utm_") || key.startsWith("pk_")
            if (isTracker) removed.add(key) else kept.add(pair)
        }
        val rebuiltQuery = if (kept.isEmpty()) null else kept.joinToString("&")
        val cleanUri = URI(
            uri.scheme,
            uri.rawAuthority,
            uri.rawPath,
            rebuiltQuery,
            uri.rawFragment,
        )
        return CleanUrlResult(cleanUri.toASCIIString(), removed, false)
    }
}
