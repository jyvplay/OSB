// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Pure-JVM **ISO/IEC 18004 QR Payload & Structured URI / Wi-Fi / vCard / otpauth Semantic
 * Classifier + Phishing Homograph Guard** for `:feature:lens`.
 */
object LensQrPayloadSemanticGuard {
    const val SCOPE =
        "Lens QR/barcode structured payload parser (WIFI:, BEGIN:VCARD, otpauth://, https://) with IDN mixed-script homograph & credential-leak guard."

    enum class QrPayloadKind { WIFI_CONFIG, VCARD_CONTACT, TOTP_SECRET, HTTPS_URL, PLAIN_TEXT }

    data class ParsedQrVerdict(
        val kind: QrPayloadKind,
        val fields: Map<String, String>,
        val phishingRiskReasons: List<String>,
        val safeAutoActionAllowed: Boolean,
    )

    fun inspectPayload(rawQrText: String): ParsedQrVerdict {
        val trimmed = rawQrText.trim()
        val risks = ArrayList<String>()
        return when {
            trimmed.startsWith("WIFI:", ignoreCase = true) -> {
                val map = parseSemicolonKvp(trimmed.substring(5))
                if (map["T"].equals("nopass", ignoreCase = true)) risks.add("OPEN_UNENCRYPTED_WIFI")
                ParsedQrVerdict(QrPayloadKind.WIFI_CONFIG, map, risks, safeAutoActionAllowed = false)
            }
            trimmed.startsWith("otpauth://", ignoreCase = true) -> {
                risks.add("SENSITIVE_2FA_SECRET_REQUIRE_EXPLICIT_VAULT_IMPORT")
                ParsedQrVerdict(QrPayloadKind.TOTP_SECRET, mapOf("uri" to trimmed), risks, safeAutoActionAllowed = false)
            }
            trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                val fields = trimmed.lines()
                    .filter { ':' in it }
                    .associate { it.substringBefore(':').uppercase() to it.substringAfter(':').trim() }
                ParsedQrVerdict(QrPayloadKind.VCARD_CONTACT, fields, risks, safeAutoActionAllowed = true)
            }
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
                if (trimmed.startsWith("http://", ignoreCase = true)) risks.add("CLEARTEXT_HTTP_SCHEME")
                val host = trimmed.substringAfter("://").substringBefore('/').substringBefore(':').substringAfterLast('@')
                if ('@' in trimmed.substringAfter("://").substringBefore('/')) risks.add("USERINFO_AT_SIGN_SPOOF")
                if (hasMixedLatinCyrillic(host) || host.startsWith("xn--", ignoreCase = true)) {
                    risks.add("IDN_PUNYCODE_OR_MIXED_SCRIPT_HOMOGRAPH")
                }
                ParsedQrVerdict(QrPayloadKind.HTTPS_URL, mapOf("host" to host, "url" to trimmed), risks, risks.isEmpty())
            }
            else -> ParsedQrVerdict(QrPayloadKind.PLAIN_TEXT, mapOf("text" to trimmed), risks, safeAutoActionAllowed = true)
        }
    }

    private fun hasMixedLatinCyrillic(host: String): Boolean {
        val hasLatin = host.any { it in 'a'..'z' || it in 'A'..'Z' }
        val hasCyrillic = host.any { it.code in 0x0400..0x04FF }
        return hasLatin && hasCyrillic
    }

    private fun parseSemicolonKvp(s: String): Map<String, String> =
        s.split(';').mapNotNull { part ->
            val idx = part.indexOf(':')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
}
