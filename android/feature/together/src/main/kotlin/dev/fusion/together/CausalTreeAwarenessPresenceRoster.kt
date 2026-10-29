// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * Commutative LWW-Element-Set **Collaborative Awareness Presence & Caret Roster** (inspired by Yjs
 * Awareness CRDT) merging out-of-order peer cursor positions with monotonic Lamport clocks and
 * deterministic heartbeat lease expiration for Fusion Together!
 */
object CausalTreeAwarenessPresenceRoster {
    const val SCOPE =
        "Commutative Lamport-clock LWW collaborative cursor/selection awareness CRDT roster with lease-TTL eviction."

    data class PeerPresence(
        val peerId: String,
        val displayName: String,
        val documentId: String,
        val caretOffset: Int,
        val selectionEndOffset: Int,
        val lamportClock: Long,
        val lastHeartbeatMillis: Long,
    )

    class Roster(private val leaseTimeoutMillis: Long = 15_000L) {
        private val peers = LinkedHashMap<String, PeerPresence>()

        fun mergeUpdate(incoming: PeerPresence): Boolean {
            val cur = peers[incoming.peerId]
            if (cur == null || incoming.lamportClock > cur.lamportClock ||
                (incoming.lamportClock == cur.lamportClock && incoming.lastHeartbeatMillis > cur.lastHeartbeatMillis)
            ) {
                peers[incoming.peerId] = incoming
                return true
            }
            return false
        }

        fun activePeers(documentId: String, nowMillis: Long): List<PeerPresence> {
            peers.entries.removeIf { (_, p) -> nowMillis - p.lastHeartbeatMillis > leaseTimeoutMillis }
            return peers.values
                .filter { it.documentId == documentId }
                .sortedBy { it.peerId }
        }
    }
}
