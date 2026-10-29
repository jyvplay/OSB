package dev.fusion.together

/**
 * Clean-room Commutative Replicated Growable Array (`RGA`) Collaborative Text CRDT Engine
 * (Apache-2.0).
 *
 * Guarantees Strong Eventual Consistency (`SEC`): any two replicas applying the same set of
 * `Insert` and `Delete` operations in ANY causal order converge to the exact same UTF-16 string!
 */
object YjsRgaTextCrdtEngine {
    const val SCOPE =
        "Commutative Replicated Growable Array (RGA) collaborative text CRDT guaranteeing strong eventual convergence"

    data class OpId(val lamport: Long, val replicaId: String) : Comparable<OpId> {
        override fun compareTo(other: OpId): Int =
            if (lamport != other.lamport) lamport.compareTo(other.lamport) else replicaId.compareTo(other.replicaId)
    }

    sealed interface CrdtOp {
        data class Insert(val id: OpId, val afterId: OpId?, val ch: Char) : CrdtOp
        data class Delete(val targetId: OpId) : CrdtOp
    }

    private class Element(val id: OpId, val afterId: OpId?, val ch: Char, var tombstoned: Boolean = false)

    class Replica(val replicaId: String) {
        private var lamport = 0L
        private val elements = ArrayList<Element>()
        private val seenInserts = HashSet<OpId>()
        private val pendingDeletes = HashSet<OpId>()

        fun localInsert(visibleIndex: Int, ch: Char): CrdtOp.Insert {
            val anchor = visibleElementBefore(visibleIndex)
            val id = OpId(++lamport, replicaId)
            val op = CrdtOp.Insert(id, anchor?.id, ch)
            apply(op)
            return op
        }

        fun localDelete(visibleIndex: Int): CrdtOp.Delete {
            val vis = elements.filter { !it.tombstoned }
            require(visibleIndex in vis.indices) { "Visible index $visibleIndex out of bounds" }
            val op = CrdtOp.Delete(vis[visibleIndex].id)
            apply(op)
            return op
        }

        fun apply(op: CrdtOp) {
            when (op) {
                is CrdtOp.Insert -> {
                    if (op.id in seenInserts) return
                    val anchorIdx = if (op.afterId == null) -1 else {
                        val idx = elements.indexOfFirst { it.id == op.afterId }
                        require(idx >= 0) { "Causal violation: missing parent ${op.afterId}" }
                        idx
                    }
                    seenInserts.add(op.id)
                    lamport = maxOf(lamport, op.id.lamport)
                    // RGA tie-breaking: skip right past existing siblings whose OpId > op.id!
                    var insPos = anchorIdx + 1
                    while (insPos < elements.size) {
                        val cur = elements[insPos]
                        // Stop if cur's parent comes at or before anchorIdx (outside subtree) AND cur.id < op.id
                        val curParentIdx = if (cur.afterId == null) -1 else elements.indexOfFirst { it.id == cur.afterId }
                        if (curParentIdx < anchorIdx) break
                        if (curParentIdx == anchorIdx && cur.id < op.id) break
                        insPos++
                    }
                    elements.add(insPos, Element(op.id, op.afterId, op.ch, op.id in pendingDeletes))
                }
                is CrdtOp.Delete -> {
                    pendingDeletes.add(op.targetId)
                    elements.firstOrNull { it.id == op.targetId }?.let { it.tombstoned = true }
                }
            }
        }

        fun text(): String = buildString {
            for (e in elements) if (!e.tombstoned) append(e.ch)
        }

        private fun visibleElementBefore(visibleIndex: Int): Element? {
            if (visibleIndex == 0) return null
            var seen = 0
            for (e in elements) {
                if (!e.tombstoned) {
                    seen++
                    if (seen == visibleIndex) return e
                }
            }
            throw IndexOutOfBoundsException("visibleIndex=$visibleIndex")
        }
    }
}
