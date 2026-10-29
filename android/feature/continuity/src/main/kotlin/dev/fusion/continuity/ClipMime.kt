// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

import java.nio.charset.StandardCharsets

/**
 * Clean-room cross-device clipboard MIME negotiation & local-URI / active-markup sanitizer
 * for `:feature:continuity`.
 *
 * Prevents three real cross-device continuity hazards:
 * 1. Oversized clipboard bombs exhausting transport memory (`MAX_PAYLOAD_BYTES = 1 MiB`),
 * 2. Local-authority URI leaks (`file:///home/...`, `content://...`, `intent://...`, `javascript:`)
 *    inside `text/uri-list` that either leak host filesystem paths or dangle on the peer device,
 * 3. Active `text/html` script/event-handler payloads (`<script`, `onerror=`, `javascript:`) —
 *    HTML-only clips are downgraded to `text/plain` when a plain representation is offered, or
 *    refused fail-closed when no safe representation exists.
 */
object ClipMime {
    const val SCOPE =
        "Cross-device clipboard MIME selection (text/markdown, text/plain, text/uri-list) with 1 MiB cap, local-URI stripping, and text/html downgrade/refusal; image/png raster transcoding out of scope."

    const val MAX_PAYLOAD_BYTES = 1_048_576
    private val ALLOWED_REMOTE_SCHEMES = setOf("https", "http", "mailto")

    enum class Outcome { ACCEPTED, DOWNGRADED_FROM_HTML, REJECTED }

    data class Offer(
        val offeredMimes: List<String>,
        val plainText: String? = null,
        val markdownText: String? = null,
        val htmlText: String? = null,
        val uriList: List<String> = emptyList(),
    )

    data class SanitizedClip(
        val outcome: Outcome,
        val selectedMime: String?,
        val payloadText: String?,
        val safeUris: List<String>,
        val strippedUriCount: Int,
        val reason: String,
    )

    fun negotiate(offer: Offer): SanitizedClip {
        val normMimes = offer.offeredMimes.map { it.substringBefore(';').trim().lowercase() }.filter { it.isNotEmpty() }
        if (normMimes.isEmpty()) {
            return SanitizedClip(Outcome.REJECTED, null, null, emptyList(), 0, "no MIME types offered")
        }

        // Priority 1: text/uri-list when offered with URIs
        if ("text/uri-list" in normMimes && offer.uriList.isNotEmpty()) {
            val safe = ArrayList<String>()
            var stripped = 0
            for (raw in offer.uriList) {
                val u = raw.trim()
                if (u.isEmpty() || u.startsWith("#")) continue
                if (isSafeRemoteUri(u)) safe.add(u) else stripped++
            }
            if (safe.isEmpty()) {
                return SanitizedClip(
                    Outcome.REJECTED, null, null, emptyList(), stripped,
                    "all offered URIs used disallowed local/executable schemes",
                )
            }
            val joined = safe.joinToString("\r\n")
            if (!fitsBudget(joined)) {
                return SanitizedClip(Outcome.REJECTED, null, null, emptyList(), stripped, "URI list exceeds 1 MiB")
            }
            return SanitizedClip(Outcome.ACCEPTED, "text/uri-list", joined, safe, stripped, "accepted safe remote URIs")
        }

        // Priority 2: text/markdown when offered (safe plain markup)
        if ("text/markdown" in normMimes && offer.markdownText != null) {
            if (!fitsBudget(offer.markdownText)) {
                return SanitizedClip(Outcome.REJECTED, null, null, emptyList(), 0, "markdown exceeds 1 MiB")
            }
            return SanitizedClip(
                Outcome.ACCEPTED, "text/markdown", sanitizeControlChars(offer.markdownText),
                emptyList(), 0, "accepted markdown",
            )
        }

        // Priority 3: text/html offered alongside text/plain -> force safe downgrade to text/plain
        val hadHtml = "text/html" in normMimes || offer.htmlText != null
        if ("text/plain" in normMimes && offer.plainText != null) {
            if (!fitsBudget(offer.plainText)) {
                return SanitizedClip(Outcome.REJECTED, null, null, emptyList(), 0, "plain text exceeds 1 MiB")
            }
            val clean = sanitizeControlChars(offer.plainText)
            return if (hadHtml) {
                SanitizedClip(
                    Outcome.DOWNGRADED_FROM_HTML, "text/plain", clean,
                    emptyList(), 0, "downgraded text/html to text/plain",
                )
            } else {
                SanitizedClip(Outcome.ACCEPTED, "text/plain", clean, emptyList(), 0, "accepted plain text")
            }
        }

        return SanitizedClip(
            Outcome.REJECTED, null, null, emptyList(), 0,
            if (hadHtml) "text/html refused without text/plain fallback" else "unsupported clipboard MIME set",
        )
    }

    private fun fitsBudget(s: String): Boolean =
        s.toByteArray(StandardCharsets.UTF_8).size <= MAX_PAYLOAD_BYTES

    private fun isSafeRemoteUri(uri: String): Boolean {
        if (uri.any { it.code <= 0x20 || it.code == 0x7F }) return false
        val colon = uri.indexOf(':')
        if (colon <= 0) return false
        val scheme = uri.substring(0, colon).lowercase()
        if (scheme !in ALLOWED_REMOTE_SCHEMES) return false
        if (scheme == "http" || scheme == "https") {
            return uri.startsWith("$scheme://", ignoreCase = true) && uri.length > scheme.length + 3
        }
        return uri.length > scheme.length + 1
    }

    /** Strips NUL and C0 control chars except `\t`, `\n`, `\r`. */
    private fun sanitizeControlChars(s: String): String = buildString(s.length) {
        for (c in s) {
            if (c == '\t' || c == '\n' || c == '\r' || (c.code >= 0x20 && c.code != 0x7F)) {
                append(c)
            }
        }
    }
}
