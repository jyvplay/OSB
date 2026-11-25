// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Bounded per-capability rate quota ledger.
 */
class CapabilitiesRateQuotaLedger private constructor(private val maxPerCap: Int) {
    companion object {
        fun create(maxPerCap: Int): CapabilitiesRateQuotaLedger {
            require(maxPerCap >= 0) { "negative maxPerCap" }
            return CapabilitiesRateQuotaLedger(maxPerCap)
        }
    }

    private val counts = HashMap<String, Int>()

    fun tryAcquire(cap: String): Boolean {
        val cur = counts.getOrDefault(cap, 0)
        if (cur >= maxPerCap) return false
        counts[cap] = cur + 1
        return true
    }
}
