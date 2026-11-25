// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Bounded URL scheme allowlist validator.
 */
class UrlSchemeAllowlist private constructor() {
    companion object {
        const val MAX_URL_LEN = 4096
        val DEFAULT_ALLOWED = setOf("https", "http", "about", "blob")

        fun isAllowed(url: String, allowedSchemes: Set<String> = DEFAULT_ALLOWED): Boolean {
            if (url.isEmpty() || url.length > MAX_URL_LEN) return false
            val colon = url.indexOf(':')
            if (colon <= 0) return false
            val scheme = url.substring(0, colon).lowercase()
            return scheme in allowedSchemes
        }
    }
}
