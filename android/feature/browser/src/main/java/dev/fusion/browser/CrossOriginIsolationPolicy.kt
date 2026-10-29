// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

/**
 * W3C / WHATWG Cross-Origin Isolation (`COOP` + `COEP` + `CORP` + Chrome 137+ `Document-Isolation-Policy`)
 * and `Sec-Fetch-*` Fetch Metadata CSRF/XS-Leak request firewall.
 *
 * Determines whether a document achieves `window.crossOriginIsolated == true` (required to unlock
 * `SharedArrayBuffer`, high-resolution timers, and multithreaded Wasm safely against Spectre),
 * evaluates subresource `Cross-Origin-Resource-Policy` admission, and blocks cross-site state-mutating
 * or framing XS-Leak requests using `Sec-Fetch-Site`, `Sec-Fetch-Mode`, and `Sec-Fetch-Dest`.
 */
object CrossOriginIsolationPolicy {
    const val SCOPE =
        "W3C/WHATWG COOP + COEP + CORP + Document-Isolation-Policy crossOriginIsolated state evaluator and Sec-Fetch-Site/Mode/Dest CSRF/XS-Leak request firewall."

    enum class CoopValue { UNSAFE_NONE, SAME_ORIGIN_ALLOW_POPUPS, SAME_ORIGIN }
    enum class CoepValue { UNSAFE_NONE, REQUIRE_CORP, CREDENTIALLESS }
    enum class DipValue { NONE, ISOLATE_AND_REQUIRE_CORP, ISOLATE_AND_CREDENTIALLESS }
    enum class CorpValue { NONE, SAME_ORIGIN, SAME_SITE, CROSS_ORIGIN }

    data class IsolationVerdict(
        val coop: CoopValue,
        val coep: CoepValue,
        val dip: DipValue,
        val crossOriginIsolated: Boolean,
        val reason: String,
    )

    fun evaluateDocumentHeaders(headers: Map<String, String>, isSecureContext: Boolean = true): IsolationVerdict {
        val norm = headers.entries.associate { it.key.trim().lowercase() to headerToken(it.value) }
        val coop = when (norm["cross-origin-opener-policy"]) {
            "same-origin" -> CoopValue.SAME_ORIGIN
            "same-origin-allow-popups" -> CoopValue.SAME_ORIGIN_ALLOW_POPUPS
            else -> CoopValue.UNSAFE_NONE
        }
        val coep = when (norm["cross-origin-embedder-policy"]) {
            "require-corp" -> CoepValue.REQUIRE_CORP
            "credentialless" -> CoepValue.CREDENTIALLESS
            else -> CoepValue.UNSAFE_NONE
        }
        val dip = when (norm["document-isolation-policy"]) {
            "isolate-and-require-corp" -> DipValue.ISOLATE_AND_REQUIRE_CORP
            "isolate-and-credentialless" -> DipValue.ISOLATE_AND_CREDENTIALLESS
            else -> DipValue.NONE
        }
        if (!isSecureContext) {
            return IsolationVerdict(coop, coep, dip, false, "non-secure context (HTTPS or localhost required)")
        }
        val viaCoopCoep = coop == CoopValue.SAME_ORIGIN &&
            (coep == CoepValue.REQUIRE_CORP || coep == CoepValue.CREDENTIALLESS)
        val viaDip = dip != DipValue.NONE
        val isolated = viaCoopCoep || viaDip
        val reason = when {
            viaCoopCoep -> "isolated via COOP=same-origin + COEP=${coep.name.lowercase()}"
            viaDip -> "isolated via Document-Isolation-Policy=${dip.name.lowercase()}"
            else -> "not isolated (requires COOP=same-origin + COEP!=unsafe-none, or DIP)"
        }
        return IsolationVerdict(coop, coep, dip, isolated, reason)
    }

    fun allowSubresourceUnderCoep(
        coep: CoepValue,
        sameOrigin: Boolean,
        sameSite: Boolean,
        hasCorsApproval: Boolean,
        corpHeader: String?,
        requestHasCredentials: Boolean = true,
    ): Boolean {
        if (coep == CoepValue.UNSAFE_NONE || sameOrigin || hasCorsApproval) return true
        val corp = when (headerToken(corpHeader.orEmpty())) {
            "same-origin" -> CorpValue.SAME_ORIGIN
            "same-site" -> CorpValue.SAME_SITE
            "cross-origin" -> CorpValue.CROSS_ORIGIN
            else -> CorpValue.NONE
        }
        return when (coep) {
            CoepValue.REQUIRE_CORP -> corp == CorpValue.CROSS_ORIGIN || (corp == CorpValue.SAME_SITE && sameSite)
            CoepValue.CREDENTIALLESS -> !requestHasCredentials || corp == CorpValue.CROSS_ORIGIN || (corp == CorpValue.SAME_SITE && sameSite)
            CoepValue.UNSAFE_NONE -> true
        }
    }

    /** W3C Fetch Metadata Resource Isolation Policy (`Sec-Fetch-Site`, `Sec-Fetch-Mode`, `Sec-Fetch-Dest`). */
    fun allowFetchMetadataRequest(method: String, headers: Map<String, String>): Boolean {
        val norm = headers.entries.associate { it.key.trim().lowercase() to it.value.trim().lowercase() }
        val site = norm["sec-fetch-site"] ?: return true // non-browser / legacy client without Fetch Metadata
        if (site == "same-origin" || site == "none") return true
        val mode = norm["sec-fetch-mode"].orEmpty()
        val dest = norm["sec-fetch-dest"].orEmpty()
        val m = method.trim().uppercase()
        // Allow safe top-level link navigations (`GET` + `mode=navigate` + non-embed/object dest).
        if (m == "GET" && mode == "navigate" && dest !in setOf("object", "embed", "iframe", "frame")) {
            return true
        }
        return false
    }

    private fun headerToken(raw: String): String =
        raw.substringBefore(';').trim().lowercase()
}
