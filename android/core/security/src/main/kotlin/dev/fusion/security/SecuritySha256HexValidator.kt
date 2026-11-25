// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Bounded 64-hex-character SHA-256 digest validator.
 */
class SecuritySha256HexValidator private constructor() {
    companion object {
        private val HEX_RE = Regex("^[0-9a-fA-F]{64}$")

        fun isValid(digest: String): Boolean = HEX_RE.matches(digest)
    }
}
