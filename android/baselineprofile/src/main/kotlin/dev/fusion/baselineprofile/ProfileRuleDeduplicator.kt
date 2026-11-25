// SPDX-License-Identifier: Apache-2.0
package dev.fusion.baselineprofile

/**
 * Bounded ART baseline profile rule deduplicator and sorter.
 */
class ProfileRuleDeduplicator private constructor() {
    companion object {
        fun deduplicateAndSort(rules: List<String>): List<String> =
            rules.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet().toList()
    }
}
