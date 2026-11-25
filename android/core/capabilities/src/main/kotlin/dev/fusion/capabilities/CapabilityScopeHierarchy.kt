// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Bounded hierarchical dot-separated capability scope matcher.
 */
class CapabilityScopeHierarchy private constructor() {
    companion object {
        const val MAX_SCOPE_LEN = 128

        fun implies(grantedScope: String, requiredScope: String): Boolean {
            require(grantedScope.isNotEmpty() && grantedScope.length <= MAX_SCOPE_LEN) { "invalid grantedScope" }
            require(requiredScope.isNotEmpty() && requiredScope.length <= MAX_SCOPE_LEN) { "invalid requiredScope" }
            if (grantedScope == requiredScope) return true
            return requiredScope.startsWith("$grantedScope.")
        }
    }
}
