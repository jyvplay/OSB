// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Deterministic PII redaction for shareable diagnostics text. Replaces email
 * addresses, phone-like digit runs, bearer/token secrets and absolute
 * device paths with bracketed tags. Idempotent: scrubbing twice changes
 * nothing the second time. Heuristic shapes only — never a leak proof and
 * never a substitute for not collecting the data. Caps input at 64 KiB.
 */
object LogScrubber {
    const val MAX_CHARS = 65536
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?<!\\d)(?:\\+\\d{1,3}[- ]?)?(?:\\d[- .]?){7,14}\\d(?!\\d)")
    private val BEARER = Regex("(?i)(bearer\\s+)[A-Za-z0-9\\-._~+/=]+")
    private val TOKEN = Regex("(?i)(token=)[^\\s&;]+")
    private val PATH = Regex("/(data|sdcard|storage|system|vendor)/[^\\s:'\"]*")

    fun scrub(text: String): String {
        require(text.length <= MAX_CHARS) { "input over $MAX_CHARS chars" }
        var out = EMAIL.replace(text, "[email]")
        out = PHONE.replace(out, "[phone]")
        out = BEARER.replace(out, "$1[secret]")
        out = TOKEN.replace(out, "$1[secret]")
        out = PATH.replace(out, "[path]")
        return out
    }

    /** True when any redactable shape is present. Same grammar as [scrub]. */
    fun containsSensitive(text: String): Boolean {
        require(text.length <= MAX_CHARS) { "input over $MAX_CHARS chars" }
        return EMAIL.containsMatchIn(text) || PHONE.containsMatchIn(text) ||
            BEARER.containsMatchIn(text) || TOKEN.containsMatchIn(text) ||
            PATH.containsMatchIn(text)
    }
}
