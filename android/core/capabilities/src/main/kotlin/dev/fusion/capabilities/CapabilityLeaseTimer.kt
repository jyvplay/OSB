// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Bounded time-limited capability lease tracker. Grants, revokes, and
 * checks active status of capability leases against caller-supplied [nowMs].
 */
class CapabilityLeaseTimer private constructor() {
    companion object {
        const val MAX_LEASES = 512
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")

        data class Lease(val leaseId: String, val capability: String, val expiresAtMs: Long)

        fun empty(): CapabilityLeaseTimer = CapabilityLeaseTimer()
    }

    private val leases = LinkedHashMap<String, Lease>()

    fun grant(leaseId: String, capability: String, expiresAtMs: Long) {
        require(ID_RE.matches(leaseId)) { "invalid leaseId" }
        require(ID_RE.matches(capability)) { "invalid capability" }
        require(expiresAtMs >= 0L) { "negative expiresAtMs" }
        require(leases.size < MAX_LEASES || leases.containsKey(leaseId)) { "too many leases" }
        leases[leaseId] = Lease(leaseId, capability, expiresAtMs)
    }

    fun isActive(leaseId: String, nowMs: Long): Boolean {
        require(nowMs >= 0L) { "negative nowMs" }
        val lease = leases[leaseId] ?: return false
        return nowMs < lease.expiresAtMs
    }

    fun revoke(leaseId: String): Boolean = leases.remove(leaseId) != null

    fun pruneExpired(nowMs: Long): Int {
        require(nowMs >= 0L) { "negative nowMs" }
        val before = leases.size
        leases.entries.removeIf { nowMs >= it.value.expiresAtMs }
        return before - leases.size
    }

    fun count(): Int = leases.size
}
