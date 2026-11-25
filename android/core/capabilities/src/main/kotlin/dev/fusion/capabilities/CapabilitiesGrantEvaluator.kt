// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Production-grade hierarchical RBAC capability-grant evaluator
 * with explicit deny-overrides-allow semantics.
 */
class CapabilitiesGrantEvaluator private constructor() {
    companion object {
        private fun matchesPattern(pattern: String, capability: String): Boolean {
            if (pattern == "*" || pattern == capability) return true
            if (pattern.endsWith(".*")) {
                val prefix = pattern.removeSuffix("*")
                return capability.startsWith(prefix)
            }
            return false
        }

        fun isAllowed(
            capability: String,
            allows: Set<String>,
            denies: Set<String>
        ): Boolean {
            require(capability.isNotEmpty()) { "empty capability" }
            if (denies.any { matchesPattern(it, capability) }) return false
            return allows.any { matchesPattern(it, capability) }
        }
    }
}
