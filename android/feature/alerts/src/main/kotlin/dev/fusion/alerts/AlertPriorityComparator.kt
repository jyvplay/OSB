// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Bounded alert severity priority comparator.
 */
class AlertPriorityComparator private constructor() {
    companion object {
        enum class Severity(val rank: Int) { LOW(0), NORMAL(1), HIGH(2), CRITICAL(3) }

        fun compare(a: Severity, b: Severity): Int = a.rank.compareTo(b.rank)
    }
}
