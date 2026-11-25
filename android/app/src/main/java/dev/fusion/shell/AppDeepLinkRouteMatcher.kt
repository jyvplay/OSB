// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Bounded app deep-link route matcher (`fusion://<host>/<path>`).
 */
class AppDeepLinkRouteMatcher private constructor() {
    companion object {
        fun matchHost(uri: String, allowedHosts: Set<String>): String? {
            if (!uri.startsWith("fusion://")) return null
            val rest = uri.removePrefix("fusion://")
            val host = rest.substringBefore('/').substringBefore('?').lowercase()
            return if (host in allowedHosts) host else null
        }
    }
}
