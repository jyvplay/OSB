// SPDX-License-Identifier: Apache-2.0
package dev.fusion.baselineprofile

/**
 * Bounded ART baseline profile method rule validator. Validates method rules
 * of the form `[HSP]+L<class>;-><method>(<args>)<ret>` within [MAX_RULE_LEN].
 */
class ProfileMethodSignatureValidator private constructor() {
    companion object {
        const val MAX_RULE_LEN = 512
        private val RULE_RE = Regex("^[HSP]{1,3}L[a-zA-Z0-9_/$]+;->[a-zA-Z0-9_<>\\$]+\\([^)]*\\)[VZBCSIJFDL\\[;a-zA-Z0-9_/$]+$")

        fun isValid(rule: String): Boolean {
            if (rule.isEmpty() || rule.length > MAX_RULE_LEN) return false
            return RULE_RE.matches(rule)
        }
    }
}
