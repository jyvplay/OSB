// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room `Apache-2.0` **Tor Proposal 329 (`Conflux`)** Traffic-Splitting Multipath Circuit
 * Scheduler, Disjoint-Relay Leg Verifier, 64-bit Relative Sequence Reassembly Queue, and
 * `RELAY_CONFLUX_XOFF` / `XON` (`50%` drain hysteresis) Backpressure Governor.
 *
 * Enforces Tor Prop 329 security & congestion invariants:
 * - Primary and secondary legs MUST NOT share Guard or Middle relays (prevents guard correlation
 *   double-dipping) and MUST terminate at the exact same Exit fingerprint.
 * - `LOWRTT_TOR` sends on the lowest-RTT leg with available `cwnd - inflight > 0`, falling back
 *   to secondary legs only when primary congestion window is full or `XOFF`-throttled.
 */
object TorConfluxMultipathScheduler {
    const val SCOPE =
        "Tor Proposal 329 (Conflux) multipath leg disjointness verifier, LOWRTT_TOR cwnd scheduler, 64-bit sequence reorder buffer, and XOFF/XON 50% hysteresis."

    data class ConfluxLeg(
        val legId: String,
        val guardFingerprint: String,
        val middleFingerprint: String,
        val exitFingerprint: String,
        val smoothedRttMillis: Int,
        val cwndCells: Int,
        val inflightCells: Int,
        val xoffPaused: Boolean = false,
    ) {
        val availableWindowCells: Int get() = (cwndCells - inflightCells).coerceAtLeast(0)
    }

    fun verifyDisjointLegs(legs: List<ConfluxLeg>): Pair<Boolean, String> {
        if (legs.size < 2) return false to "Conflux multipath set requires >= 2 legs"
        val exits = legs.map { it.exitFingerprint }.distinct()
        if (exits.size != 1) return false to "All Conflux legs must merge at the exact same Exit relay"
        val guards = legs.map { it.guardFingerprint }
        if (guards.distinct().size != guards.size) return false to "Prop 329 violation: Conflux legs share a Guard relay"
        val middles = legs.map { it.middleFingerprint }
        if (middles.distinct().size != middles.size) return false to "Prop 329 violation: Conflux legs share a Middle relay"
        return true to "Disjoint Guard/Middle + shared Exit verified"
    }

    fun selectTransmitLegLowRtt(legs: List<ConfluxLeg>): ConfluxLeg? =
        legs.filter { !it.xoffPaused && it.availableWindowCells > 0 }
            .minWithOrNull(compareBy<ConfluxLeg> { it.smoothedRttMillis }.thenByDescending { it.availableWindowCells })

    class ReorderQueue(private val xoffHighWaterCells: Int = 64) {
        private var nextExpectedSeq = 1L
        private val outOfOrder = java.util.TreeMap<Long, ByteArray>()
        var xoffSignaled: Boolean = false
            private set

        fun receiveCell(absSeq: Long, cellPayload: ByteArray): List<ByteArray> {
            require(absSeq >= 1L)
            if (absSeq < nextExpectedSeq || outOfOrder.containsKey(absSeq)) return emptyList() // duplicate
            outOfOrder[absSeq] = cellPayload
            val delivered = ArrayList<ByteArray>()
            while (true) {
                val head = outOfOrder.remove(nextExpectedSeq) ?: break
                delivered.add(head)
                nextExpectedSeq++
            }
            if (!xoffSignaled && outOfOrder.size >= xoffHighWaterCells) {
                xoffSignaled = true
            } else if (xoffSignaled && outOfOrder.size <= xoffHighWaterCells / 2) {
                // Prop 329 §3.3: send XON only after buffered queue drains to <= 50% of XOFF watermark!
                xoffSignaled = false
            }
            return delivered
        }

        fun bufferedOutOfOrderCount(): Int = outOfOrder.size
    }
}
