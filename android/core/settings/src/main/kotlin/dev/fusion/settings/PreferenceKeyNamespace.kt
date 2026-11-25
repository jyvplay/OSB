// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Bounded preference key namespace validator (`<domain>.<key>`).
 */
class PreferenceKeyNamespace private constructor() {
    companion object {
        const val MAX_KEY_LEN = 128
        private val KEY_RE = Regex("^([a-z][a-z0-9_-]{0,31})\\.([a-z][a-z0-9_.-]{0,94})$")

        fun extractDomain(key: String): String {
            require(key.length <= MAX_KEY_LEN) { "key too long" }
            val m = KEY_RE.matchEntire(key) ?: throw IllegalArgumentException("invalid preference key: $key")
            return m.groupValues[1]
        }
    }
}
