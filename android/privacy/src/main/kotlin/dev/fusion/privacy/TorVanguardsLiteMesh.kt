package dev.fusion.privacy

import java.security.SecureRandom

/**
 * Clean-room Tor Proposal 333 (`Vanguards-Lite`, deployed in `Arti 2.7.0` / `Tails 7.15`)
 * Layer-2 Middle-Hop Guard Pool Selector (Apache-2.0).
 *
 * Defends onion-service & sensitive circuits against Sybil guard-discovery attacks:
 * - Maintains `NUM_LAYER2_GUARDS = 4` Layer-2 relays strictly in volatile RAM (`diskPersistAllowed = false`),
 * - Draws each guard's rotation lifetime from `max(U(1..12), U(1..12))` days per Tor Prop 333 §2.1,
 * - Enforces distinct `/16` IPv4 subnet prefix AND distinct operator `familyId` across all 4 guards
 *   AND against the active Layer-1 entry guard!
 */
object TorVanguardsLiteMesh {
    const val SCOPE =
        "Tor Proposal 333 (Arti 2.7.0 / Tails 7.15) Vanguards-Lite 4-relay Layer-2 guard pool with max(U(1..12),U(1..12)) day lifetime & /16 diversity"

    const val NUM_LAYER2_GUARDS = 4
    const val MIN_LIFETIME_DAYS = 1
    const val MAX_LIFETIME_DAYS = 12
    const val DISK_PERSIST_ALLOWED = false

    data class RelayDescriptor(
        val fingerprintHex: String,
        val ipv4: String,
        val familyId: String,
        val consensusWeight: Long,
        val isMiddleCapable: Boolean = true,
    ) {
        val slash16Subnet: String
            get() {
                val octets = ipv4.split('.')
                require(octets.size == 4) { "Invalid IPv4 '$ipv4'" }
                return "${octets[0]}.${octets[1]}"
            }
    }

    data class Layer2Lease(
        val relay: RelayDescriptor,
        val chosenAtEpochSec: Long,
        val expiresAtEpochSec: Long,
        val lifetimeDays: Int,
    )

    class Pool(private val rng: SecureRandom = SecureRandom()) {
        private val activeLeases = ArrayList<Layer2Lease>(NUM_LAYER2_GUARDS)

        /** Tor Proposal 333 §2.1 exact `max(X1, X2)` distribution over `1..12` days. */
        fun sampleLifetimeDays(draw1To12: () -> Int = { 1 + rng.nextInt(12) }): Int {
            val d1 = draw1To12().coerceIn(MIN_LIFETIME_DAYS, MAX_LIFETIME_DAYS)
            val d2 = draw1To12().coerceIn(MIN_LIFETIME_DAYS, MAX_LIFETIME_DAYS)
            return maxOf(d1, d2)
        }

        fun replenish(
            nowEpochSec: Long,
            entryGuard: RelayDescriptor,
            consensus: List<RelayDescriptor>,
        ): List<Layer2Lease> {
            activeLeases.removeAll { it.expiresAtEpochSec <= nowEpochSec }
            val usedFingerprints = HashSet<String>().apply {
                add(entryGuard.fingerprintHex)
                activeLeases.forEach { add(it.relay.fingerprintHex) }
            }
            val usedSubnets = HashSet<String>().apply {
                add(entryGuard.slash16Subnet)
                activeLeases.forEach { add(it.relay.slash16Subnet) }
            }
            val usedFamilies = HashSet<String>().apply {
                add(entryGuard.familyId)
                activeLeases.forEach { add(it.relay.familyId) }
            }

            while (activeLeases.size < NUM_LAYER2_GUARDS) {
                val eligible = consensus.filter { r ->
                    r.isMiddleCapable &&
                        r.consensusWeight > 0L &&
                        r.fingerprintHex !in usedFingerprints &&
                        r.slash16Subnet !in usedSubnets &&
                        r.familyId !in usedFamilies
                }
                require(eligible.isNotEmpty()) {
                    "Insufficient diverse middle relays to fill Vanguards-Lite Layer-2 pool (${activeLeases.size}/$NUM_LAYER2_GUARDS)"
                }
                val chosen = weightedPick(eligible)
                val days = sampleLifetimeDays()
                activeLeases.add(
                    Layer2Lease(
                        relay = chosen,
                        chosenAtEpochSec = nowEpochSec,
                        expiresAtEpochSec = nowEpochSec + days * 86_400L,
                        lifetimeDays = days,
                    ),
                )
                usedFingerprints.add(chosen.fingerprintHex)
                usedSubnets.add(chosen.slash16Subnet)
                usedFamilies.add(chosen.familyId)
            }
            return activeLeases.toList()
        }

        fun wipeVolatilePool(): Int {
            val count = activeLeases.size
            activeLeases.clear()
            return count
        }

        private fun weightedPick(candidates: List<RelayDescriptor>): RelayDescriptor {
            val total = candidates.sumOf { it.consensusWeight }
            var ticket = (rng.nextLong().ushr(1)) % total
            for (c in candidates) {
                if (ticket < c.consensusWeight) return c
                ticket -= c.consensusWeight
            }
            return candidates.last()
        }
    }
}
