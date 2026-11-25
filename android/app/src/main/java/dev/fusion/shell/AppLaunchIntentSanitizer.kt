// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Bounded app launch intent URI sanitizer.
 */
class AppLaunchIntentSanitizer private constructor() {
    companion object {
        const val MAX_URI_LEN = 2048
        val ALLOWED_SCHEMES = setOf("fusion", "https", "content")

        fun isSafe(uri: String): Boolean {
            if (uri.isEmpty() || uri.length > MAX_URI_LEN) return false
            for (ch in uri) {
                if (ch.code < 0x20 || ch.code == 0x7F) return false
            }
            val colon = uri.indexOf(':')
            if (colon <= 0) return false
            val scheme = uri.substring(0, colon).lowercase()
            return scheme in ALLOWED_SCHEMES
        }
    }
}
