// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Bounded CSRF token validator with constant-time comparison and expiry.
 */
class CsrfTokenValidator private constructor() {
    companion object {
        const val MIN_TOKEN_LEN = 16
        const val MAX_TOKEN_LEN = 256

        fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var diff = 0
            for (i in a.indices) {
                diff = diff or (a[i].code xor b[i].code)
            }
            return diff == 0
        }

        fun validate(expected: String, provided: String, expiresAtMs: Long, nowMs: Long): Boolean {
            require(expected.length in MIN_TOKEN_LEN..MAX_TOKEN_LEN) { "expected token length out of bounds" }
            require(nowMs >= 0L && expiresAtMs >= 0L) { "negative timestamp" }
            if (nowMs >= expiresAtMs) return false
            if (provided.length !in MIN_TOKEN_LEN..MAX_TOKEN_LEN) return false
            return constantTimeEquals(expected, provided)
        }
    }
}
