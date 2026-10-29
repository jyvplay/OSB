// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Lock-screen & screen-sharing **Sensitive OTP / 2FA / Magic-Link / Bearer-Token Notification
 * Redactor** (`Android 15+` sensitive notification protection parity) masking verification codes
 * before external display mirroring (`MediaProjection`) or lock-screen preview rendering!
 */
object SensitiveOtpNotificationRedactor {
    const val SCOPE =
        "OTP 4-8 digit verification code, Bearer JWT, and magic-link token detector/redactor for lock-screen and screen-share alerts."

    data class RedactionResult(
        val containsSensitiveSecret: Boolean,
        val publicPreviewText: String,
        val matchedCategories: Set<String>,
    )

    private val OTP_CONTEXT_RE = Regex("(?i)\\b(code|otp|passcode|verification|2fa|pin|tan)\\b")
    private val DIGIT_CODE_RE = Regex("\\b\\d{4,8}\\b")
    private val BEARER_RE = Regex("(?i)Bearer\\s+[A-Za-z0-9._\\-~+/]+=*")
    private val MAGIC_LINK_RE = Regex("https?://[^\\s]+(?:token|magic|verify|auth|reset)=[^\\s&]+", RegexOption.IGNORE_CASE)

    fun redactForUntrustedSurface(title: String, body: String): RedactionResult {
        val combined = "$title — $body"
        val cats = LinkedHashSet<String>()
        var redacted = body

        if (OTP_CONTEXT_RE.containsMatchIn(combined) && DIGIT_CODE_RE.containsMatchIn(redacted)) {
            cats.add("OTP_CODE")
            redacted = DIGIT_CODE_RE.replace(redacted, "••••••")
        }
        if (BEARER_RE.containsMatchIn(redacted)) {
            cats.add("BEARER_TOKEN")
            redacted = BEARER_RE.replace(redacted, "Bearer [REDACTED]")
        }
        if (MAGIC_LINK_RE.containsMatchIn(redacted)) {
            cats.add("AUTH_MAGIC_LINK")
            redacted = MAGIC_LINK_RE.replace(redacted, "[REDACTED-SIGNIN-LINK]")
        }
        return RedactionResult(cats.isNotEmpty(), redacted, cats)
    }
}
