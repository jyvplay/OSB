// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * Clean-room Roh et al. (`JPDC 2011`) Replicated Growable Array (`RGA`) collaborative text CRDT (`RgaTextSequence`).
 *
 * Proves **Strong Eventual Consistency** across concurrent offline-first Writer/Notes peers:
 * - Every inserted character carries a globally total-ordered `OpId(lamport, replicaId)` and `after: OpId?`
 *   (the `OpId` of its immediate left neighbor at insertion time, or `null` for head of document).
 * - Concurrent inserts sharing the same `after` parent are ordered deterministically in descending `(lamport, replicaId)`
 *   order, skipping over any subtree rooted at higher-priority siblings — guaranteeing **exact commutativity**
 *   and **idempotency** regardless of network arrival order!
 * - Deletes mark tombstones (`deleted = true`) idempotently so late-arriving causal children still anchor cleanly.
 */
class RgaTextSequence(val localReplicaId: String) {
    companion object {
        const val SCOPE =
            "Roh et al. RGA character-level text CRDT (commuting/idempotent RemoteOp.Insert & RemoteOp.Delete, causal buffer for out-of-order ops, Lamport clock); tombstone GC compaction out of scope."
    }

    init {
        require(localReplicaId.isNotBlank()) { "localReplicaId must not be blank" }
    }

    data class OpId(val lamport: Long, val replicaId: String) : Comparable<OpId> {
        override fun compareTo(other: OpId): Int {
            val c = lamport.compareTo(other.lamport)
            return if (c != 0) c else replicaId.compareTo(other.replicaId)
        }
    }

    sealed interface RemoteOp {
        data class Insert(val id: OpId, val after: OpId?, val ch: Char) : RemoteOp
        data class Delete(val target: OpId) : RemoteOp
    }

    private class Element(
        val id: OpId,
        val after: OpId?,
        val ch: Char,
        var deleted: Boolean = false,
    )

    private val sequence = ArrayList<Element>()
    private val indexById = HashMap<OpId, Element>()
    private val pendingDeletes = HashSet<OpId>()
    private val pendingInserts = ArrayList<RemoteOp.Insert>()
    var lamportClock: Long = 0L
        private set

    fun localInsert(visibleIndex: Int, ch: Char): RemoteOp.Insert {
        val afterId = visibleElementBefore(visibleIndex)?.id
        val id = OpId(++lamportClock, localReplicaId)
        val op = RemoteOp.Insert(id, afterId, ch)
        apply(op)
        return op
    }

    fun localDelete(visibleIndex: Int): RemoteOp.Delete {
        val elem = visibleElementAt(visibleIndex)
            ?: throw IndexOutOfBoundsException("Visible index $visibleIndex out of range")
        val op = RemoteOp.Delete(elem.id)
        apply(op)
        return op
    }

    fun apply(op: RemoteOp) {
        when (op) {
            is RemoteOp.Insert -> {
                if (op.id.lamport > lamportClock) lamportClock = op.id.lamport
                if (indexById.containsKey(op.id)) return // Idempotent duplicate delivery
                if (op.after != null && !indexById.containsKey(op.after)) {
                    if (pendingInserts.none { it.id == op.id }) pendingInserts.add(op)
                    return
                }
                integrateInsert(op)
                drainCausalBuffer()
            }
            is RemoteOp.Delete -> {
                val existing = indexById[op.target]
                if (existing != null) existing.deleted = true
                else pendingDeletes.add(op.target)
            }
        }
    }

    fun visibleText(): String = buildString(sequence.size) {
        for (e in sequence) if (!e.deleted) append(e.ch)
    }

    private fun integrateInsert(op: RemoteOp.Insert) {
        val parentIdx = if (op.after == null) -1 else sequence.indexOfFirst { it.id == op.after }
        var idx = parentIdx + 1
        // RGA tie-breaking invariant: skip right past any existing element (and its causal descendants)
        // whose OpId > op.id, stopping as soon as we hit an element anchored outside the parent subtree
        // or a sibling with OpId < op.id.
        val skippedSubtreeIds = HashSet<OpId>()
        if (op.after != null) skippedSubtreeIds.add(op.after)
        while (idx < sequence.size) {
            val cand = sequence[idx]
            val candParentInSubtree = if (op.after == null) {
                cand.after == null || cand.after in skippedSubtreeIds
            } else {
                cand.after in skippedSubtreeIds
            }
            if (!candParentInSubtree) break
            val isSiblingOfSameParent = cand.after == op.after
            if (isSiblingOfSameParent && cand.id < op.id) break
            skippedSubtreeIds.add(cand.id)
            idx++
        }
        val elem = Element(op.id, op.after, op.ch, deleted = pendingDeletes.remove(op.id))
        sequence.add(idx, elem)
        indexById[op.id] = elem
    }

    private fun drainCausalBuffer() {
        var progress = true
        while (progress && pendingInserts.isNotEmpty()) {
            progress = false
            val iter = pendingInserts.iterator()
            while (iter.hasNext()) {
                val cand = iter.next()
                if (cand.after == null || indexById.containsKey(cand.after)) {
                    iter.remove()
                    if (!indexById.containsKey(cand.id)) integrateInsert(cand)
                    progress = true
                }
            }
        }
    }

    private fun visibleElementBefore(visibleIndex: Int): Element? {
        require(visibleIndex >= 0) { "Negative visibleIndex: $visibleIndex" }
        if (visibleIndex == 0) return null
        return visibleElementAt(visibleIndex - 1)
            ?: throw IndexOutOfBoundsException("Visible index $visibleIndex exceeds length")
    }

    private fun visibleElementAt(visibleIndex: Int): Element? {
        var seen = 0
        for (e in sequence) {
            if (!e.deleted) {
                if (seen == visibleIndex) return e
                seen++
            }
        }
        return null
    }
}
