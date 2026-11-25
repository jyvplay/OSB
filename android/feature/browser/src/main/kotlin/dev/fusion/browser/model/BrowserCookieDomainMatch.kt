// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Bounded RFC 6265 cookie domain matcher.
 */
class BrowserCookieDomainMatch private constructor() {
    companion object {
        fun matches(requestHost: String, cookieDomain: String, hostOnly: Boolean): Boolean {
            val h = requestHost.lowercase()
            val d = cookieDomain.lowercase().trimStart('.')
            if (h == d) return true
            if (hostOnly) return false
            return h.endsWith(".$d")
        }
    }
}
