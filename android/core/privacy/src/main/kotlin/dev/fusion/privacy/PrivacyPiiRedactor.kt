// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Production-grade PII redactor for emails and IPv4 addresses.
 */
class PrivacyPiiRedactor private constructor() {
    companion object {
        private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val IPV4_REGEX = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

        fun redact(text: String): String {
            var out = EMAIL_REGEX.replace(text, "[REDACTED_EMAIL]")
            out = IPV4_REGEX.replace(out, "[REDACTED_IP]")
            return out
        }
    }
}
