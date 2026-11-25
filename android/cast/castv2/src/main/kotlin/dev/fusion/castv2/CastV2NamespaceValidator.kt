// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Bounded CastV2 namespace validator (`urn:x-cast:...`).
 */
class CastV2NamespaceValidator private constructor() {
    companion object {
        const val MAX_NS_LEN = 128
        private val NS_RE = Regex("^urn:x-cast:[a-zA-Z0-9._-]+$")

        fun isValid(ns: String): Boolean {
            if (ns.isEmpty() || ns.length > MAX_NS_LEN) return false
            return NS_RE.matches(ns)
        }
    }
}
