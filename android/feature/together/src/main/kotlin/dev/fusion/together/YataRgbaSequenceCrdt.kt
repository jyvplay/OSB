// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * Clean-room **RGA / YATA Collaborative Plain-Text Sequence CRDT** (`Replicated Growable Array`).
 *
 * Guarantees **Strong Eventual Consistency (`SEC`)**: any set of concurrent character insertions
 * and tombstoned deletions applied in ANY causal delivery order across offline/online peers
 * converges to the **exact same bit-identical string**!
 */
class YataRgbaSequenceCrdt {
    companion object {
        const val SCOPE =
            "Clean-room RGA/YATA collaborative text sequence CRDT with Lamport OpId tie-breaking guaranteeing strong eventual convergence."
    }

    data class OpId(val lamport: Long, val replicaId: String) : Comparable<OpId> {
        override fun compareTo(other: OpId): Int {
            val c = lamport.compareTo(other.lamport)
            return if (c != 0) c else replicaId.compareTo(other.replicaId)
        }
    }

    sealed interface Operation {
        data class Insert(val id: OpId, val afterId: OpId?, val ch: Char) : Operation
        data class Delete(val targetId: OpId) : Operation
    }

    private data class Element(val id: OpId, val afterId: OpId?, val ch: Char, var deleted: Boolean = false)

    private val elements = ArrayList<Element>()
    private val knownIds = HashSet<OpId>()
    private var maxLamport = 0L

    fun localInsertAt(replicaId: String, visibleIndex: Int, ch: Char): Operation.Insert {
        val after = visibleElementBefore(visibleIndex)?.id
        val id = OpId(++maxLamport, replicaId)
        val op = Operation.Insert(id, after, ch)
        apply(op)
        return op
    }

    fun localDeleteAt(visibleIndex: Int): Operation.Delete {
        var vis = 0
        for (el in elements) {
            if (!el.deleted) {
                if (vis == visibleIndex) {
                    val op = Operation.Delete(el.id)
                    apply(op)
                    return op
                }
                vis++
            }
        }
        throw IndexOutOfBoundsException("visibleIndex $visibleIndex out of bounds")
    }

    fun apply(op: Operation) {
        when (op) {
            is Operation.Insert -> {
                if (!knownIds.add(op.id)) return // idempotent replay safe!
                if (op.id.lamport > maxLamport) maxLamport = op.id.lamport
                val parentIdx = if (op.afterId == null) -1 else {
                    val idx = elements.indexOfFirst { it.id == op.afterId }
                    require(idx >= 0) { "causal precondition violated: missing origin ${op.afterId}" }
                    idx
                }
                // RGA tie-breaking: skip right past existing siblings whose OpId > op.id (and their causal descendants!)
                var insIdx = parentIdx + 1
                val skippedSubtreeRoots = HashSet<OpId>()
                while (insIdx < elements.size) {
                    val cur = elements[insIdx]
                    val belongsToSkipped = cur.afterId in skippedSubtreeRoots
                    val sameOriginHigherId = cur.afterId == op.afterId && cur.id > op.id
                    if (belongsToSkipped || sameOriginHigherId) {
                        skippedSubtreeRoots.add(cur.id)
                        insIdx++
                    } else break
                }
                elements.add(insIdx, Element(op.id, op.afterId, op.ch, false))
            }
            is Operation.Delete -> {
                val hit = elements.firstOrNull { it.id == op.targetId }
                if (hit != null) hit.deleted = true
            }
        }
    }

    fun text(): String = buildString {
        for (el in elements) if (!el.deleted) append(el.ch)
    }

    private fun visibleElementBefore(visibleIndex: Int): Element? {
        require(visibleIndex >= 0) { "negative visibleIndex" }
        if (visibleIndex == 0) return null
        var seen = 0
        for (el in elements) {
            if (!el.deleted) {
                seen++
                if (seen == visibleIndex) return el
            }
        }
        throw IndexOutOfBoundsException("visibleIndex $visibleIndex > length $seen")
    }
}
