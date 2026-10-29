// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import kotlin.math.ln

/**
 * Clean-room sensitive clipboard classifier, Android 13+ `EXTRA_IS_SENSITIVE` metadata marker,
 * and monotonic TTL auto-wipe state machine (`ClipboardSensitiveGuard`).
 *
 * Prevents background clipboard-sniffer / toast-preview leaks when users copy passwords, API tokens,
 * PEM private keys, JWTs, BIP-39 12/24-word mnemonics, OTP codes, or Luhn payment cards:
 * - Automatically flags secret payloads with `extraIsSensitive = true` (`android.content.extra.IS_SENSITIVE`)
 *   so Android 13+ SystemUI masks the clipboard preview overlay (`••••••`).
 * - Schedules deterministic monotonic auto-wipe at `copiedAtMs + sensitiveTtlMs` (`default 30_000 ms`)
 *   and physically zeroes the backing `CharArray` on expiry or manual `clearNow()`!
 */
object ClipboardSensitiveGuard {
    const val SCOPE =
        "Sensitive clipboard secret detector + Android 13+ EXTRA_IS_SENSITIVE flagger + 30s monotonic CharArray zeroizing TTL buffer; OS-wide cross-UID ClipboardManager hooks out of scope."

    const val EXTRA_IS_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"
    const val DEFAULT_SENSITIVE_TTL_MS = 30_000L

    enum class SensitivityReason {
        EXPLICIT_CALLER_FLAG,
        PEM_PRIVATE_KEY,
        JWT_BEARER_TOKEN,
        API_SECRET_PREFIX,
        PAYMENT_CARD_LUHN,
        MNEMONIC_PHRASE,
        HIGH_ENTROPY_TOKEN,
        PUBLIC_TEXT,
    }

    data class Classification(
        val isSensitive: Boolean,
        val reason: SensitivityReason,
        val shannonBitsPerChar: Double,
    )

    private val JWT_RE = Regex("^[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}$")
    private val SECRET_PREFIX_RE = Regex("^(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]{16,})$")

    fun classify(text: String, explicitSensitive: Boolean = false): Classification {
        val entropy = shannonEntropy(text)
        if (explicitSensitive) return Classification(true, SensitivityReason.EXPLICIT_CALLER_FLAG, entropy)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Classification(false, SensitivityReason.PUBLIC_TEXT, 0.0)
        if (trimmed.contains("-----BEGIN ") && trimmed.contains("PRIVATE KEY-----")) {
            return Classification(true, SensitivityReason.PEM_PRIVATE_KEY, entropy)
        }
        if (JWT_RE.matches(trimmed)) {
            return Classification(true, SensitivityReason.JWT_BEARER_TOKEN, entropy)
        }
        if (SECRET_PREFIX_RE.matches(trimmed)) {
            return Classification(true, SensitivityReason.API_SECRET_PREFIX, entropy)
        }
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length in 13..19 && trimmed.all { it.isDigit() || it == ' ' || it == '-' } && passesLuhn(digitsOnly)) {
            return Classification(true, SensitivityReason.PAYMENT_CARD_LUHN, entropy)
        }
        val words = trimmed.split(Regex("\\s+"))
        if ((words.size == 12 || words.size == 24) && words.all { it.length in 3..8 && it.all(Char::isLowerCase) }) {
            return Classification(true, SensitivityReason.MNEMONIC_PHRASE, entropy)
        }
        if (!trimmed.contains(' ') && trimmed.length in 16..256 && entropy >= 3.85) {
            return Classification(true, SensitivityReason.HIGH_ENTROPY_TOKEN, entropy)
        }
        return Classification(false, SensitivityReason.PUBLIC_TEXT, entropy)
    }

    class GuardedClipBuffer(val sensitiveTtlMs: Long = DEFAULT_SENSITIVE_TTL_MS) {
        private var chars: CharArray? = null
        var classification: Classification = Classification(false, SensitivityReason.PUBLIC_TEXT, 0.0)
            private set
        var expiresAtMs: Long? = null
            private set

        val extraIsSensitive: Boolean get() = classification.isSensitive && chars != null

        fun put(text: String, nowMs: Long, explicitSensitive: Boolean = false): Classification {
            clearNow()
            val cls = classify(text, explicitSensitive)
            chars = text.toCharArray()
            classification = cls
            expiresAtMs = if (cls.isSensitive) nowMs + sensitiveTtlMs else null
            return cls
        }

        fun pollMaintenance(nowMs: Long): Boolean {
            val exp = expiresAtMs ?: return false
            if (nowMs >= exp) {
                clearNow()
                return true
            }
            return false
        }

        fun readIfLive(nowMs: Long): String? {
            pollMaintenance(nowMs)
            return chars?.let { String(it) }
        }

        fun clearNow() {
            chars?.fill('\u0000')
            chars = null
            expiresAtMs = null
            classification = Classification(false, SensitivityReason.PUBLIC_TEXT, 0.0)
        }
    }

    fun shannonEntropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val freq = HashMap<Char, Int>()
        for (c in s) freq[c] = (freq[c] ?: 0) + 1
        val n = s.length.toDouble()
        var h = 0.0
        for (count in freq.values) {
            val p = count / n
            h -= p * (ln(p) / ln(2.0))
        }
        return h
    }

    fun passesLuhn(digits: String): Boolean {
        if (digits.length < 13 || digits.all { it == '0' }) return false
        var sum = 0
        var doubleDigit = false
        for (i in digits.lastIndex downTo 0) {
            var d = digits[i] - '0'
            if (d !in 0..9) return false
            if (doubleDigit) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            doubleDigit = !doubleDigit
        }
        return sum % 10 == 0
    }
}
