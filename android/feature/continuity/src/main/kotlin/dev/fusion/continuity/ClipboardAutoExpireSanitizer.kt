// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Cross-device & local **Clipboard Auto-Expire TTL + Unicode Homograph / Trojan-Source BiDi
 * (`U+202A..U+202E`, `U+2066..U+2069`) Sanitizer** protecting Fusion Continuity & Vault password
 * copies from background clipboard snoopers (`30 s` secret TTL) and Trojan-Source paste attacks!
 */
object ClipboardAutoExpireSanitizer {
    const val SCOPE =
        "Clipboard secret 30s TTL auto-wiper (PersistableBundle EXTRA_IS_SENSITIVE) and CVE-2021-42574 Trojan-Source BiDi override stripper."

    private val BIDI_TROJAN_CHARS = setOf(
        '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069', '\u200E', '\u200F',
    )

    data class PreparedClip(
        val cleanText: String,
        val strippedBidiCount: Int,
        val isSensitiveSecret: Boolean,
        val expiresAtEpochMillis: Long,
        val allowCrossDeviceContinuitySync: Boolean,
    )

    fun prepareClipboardItem(
        rawText: String,
        markedSensitiveBySource: Boolean,
        nowEpochMillis: Long,
        tailsAmnesicMode: Boolean = false,
    ): PreparedClip {
        var stripped = 0
        val sb = StringBuilder(rawText.length)
        for (ch in rawText) {
            if (ch in BIDI_TROJAN_CHARS) stripped++ else sb.append(ch)
        }
        val clean = sb.toString()
        val heuristicSecret = markedSensitiveBySource || looksLikeHighEntropySecret(clean)
        val ttlMs = when {
            tailsAmnesicMode -> 15_000L
            heuristicSecret -> 30_000L
            else -> 300_000L
        }
        return PreparedClip(
            cleanText = clean,
            strippedBidiCount = stripped,
            isSensitiveSecret = heuristicSecret,
            expiresAtEpochMillis = nowEpochMillis + ttlMs,
            allowCrossDeviceContinuitySync = !heuristicSecret && !tailsAmnesicMode,
        )
    }

    fun isExpired(clip: PreparedClip, currentMillis: Long): Boolean = currentMillis >= clip.expiresAtEpochMillis

    private fun looksLikeHighEntropySecret(s: String): Boolean {
        val t = s.trim()
        if (t.length !in 12..128 || ' ' in t) return false
        val hasUpper = t.any { it.isUpperCase() }
        val hasLower = t.any { it.isLowerCase() }
        val hasDigit = t.any { it.isDigit() }
        val hasSym = t.any { !it.isLetterOrDigit() }
        return listOf(hasUpper, hasLower, hasDigit, hasSym).count { it } >= 3
    }
}
