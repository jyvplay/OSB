// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room Peritext-style Rich-Text Formatting Span CRDT (`Litt et al. 2021` conceptual algorithm,
 * implemented from scratch under `Apache-2.0`).
 *
 * Anchors rich-text marks (`BOLD`, `ITALIC`, `UNDERLINE`, `CODE`, `LINK`, `COMMENT`) to immutable
 * character IDs `(lamport, replicaId)` with explicit boundary expansion rules (`BEFORE` / `AFTER`)
 * so concurrent typing and overlapping formatting operations commute deterministically without
 * producing inverted or orphaned spans.
 */
object PeritextRichSpanCrdtEngine {
    enum class MarkKind(val allowsValue: Boolean, val expandEndAfter: Boolean) {
        BOLD(false, true),
        ITALIC(false, true),
        UNDERLINE(false, true),
        STRIKETHROUGH(false, true),
        INLINE_CODE(false, false),
        LINK(true, false),
        COMMENT(true, false),
    }

    data class OpId(val lamport: Long, val replicaId: String) : Comparable<OpId> {
        override fun compareTo(other: OpId): Int {
            val c = lamport.compareTo(other.lamport)
            return if (c != 0) c else replicaId.compareTo(other.replicaId)
        }
    }

    data class Anchor(val charId: OpId, val side: AnchorSide)
    enum class AnchorSide { BEFORE, AFTER }

    data class MarkOp(
        val opId: OpId,
        val kind: MarkKind,
        val action: MarkAction,
        val start: Anchor,
        val end: Anchor,
        val payload: String? = null,
    )
    enum class MarkAction { ADD, REMOVE }

    private data class CharCell(
        val id: OpId,
        val originLeft: OpId?,
        val ch: Char,
        var tombstone: Boolean = false,
    )

    data class ResolvedRun(
        val text: String,
        val activeMarks: Set<MarkKind>,
        val attributes: Map<MarkKind, String>,
    )

    class RichDocumentReplica(val replicaId: String) {
        private var clock: Long = 0L
        private val sequence = ArrayList<CharCell>()
        private val seenCharIds = HashSet<OpId>()
        private val markOps = LinkedHashMap<OpId, MarkOp>()

        private fun nextId(): OpId = OpId(++clock, replicaId)

        fun insertLocal(visibleOffset: Int, text: String): List<Pair<OpId, Pair<OpId?, Char>>> {
            require(visibleOffset in 0..visibleLength()) { "visibleOffset $visibleOffset out of range" }
            var leftId = visibleCharIdAtOrNull(visibleOffset - 1)
            val emitted = ArrayList<Pair<OpId, Pair<OpId?, Char>>>(text.length)
            for (ch in text) {
                val id = nextId()
                integrateInsert(id, leftId, ch)
                emitted.add(id to (leftId to ch))
                leftId = id
            }
            return emitted
        }

        fun deleteLocal(visibleStart: Int, visibleEndExclusive: Int): List<OpId> {
            require(visibleStart in 0..visibleEndExclusive && visibleEndExclusive <= visibleLength()) {
                "Invalid delete range $visibleStart..$visibleEndExclusive"
            }
            val targets = (visibleStart until visibleEndExclusive).map { visibleCharIdAtOrNull(it)!! }
            targets.forEach { integrateDelete(it) }
            return targets
        }

        fun formatLocal(
            visibleStart: Int,
            visibleEndExclusive: Int,
            kind: MarkKind,
            action: MarkAction = MarkAction.ADD,
            payload: String? = null,
        ): MarkOp {
            require(visibleStart in 0 until visibleEndExclusive && visibleEndExclusive <= visibleLength()) {
                "Non-empty visible range required ($visibleStart..$visibleEndExclusive)"
            }
            val startId = visibleCharIdAtOrNull(visibleStart)!!
            val endId = visibleCharIdAtOrNull(visibleEndExclusive - 1)!!
            val op = MarkOp(
                opId = nextId(),
                kind = kind,
                action = action,
                start = Anchor(startId, AnchorSide.BEFORE),
                end = Anchor(endId, if (kind.expandEndAfter) AnchorSide.AFTER else AnchorSide.BEFORE),
                payload = payload,
            )
            integrateMark(op)
            return op
        }

        fun integrateInsert(id: OpId, originLeft: OpId?, ch: Char) {
            if (!seenCharIds.add(id)) return
            clock = maxOf(clock, id.lamport)
            val leftIdx = if (originLeft == null) -1 else sequence.indexOfFirst { it.id == originLeft }
            var insertAt = leftIdx + 1
            while (insertAt < sequence.size) {
                val existing = sequence[insertAt]
                val exLeftIdx = if (existing.originLeft == null) -1 else sequence.indexOfFirst { it.id == existing.originLeft }
                if (exLeftIdx < leftIdx) break
                if (exLeftIdx == leftIdx && existing.id < id) break
                insertAt++
            }
            sequence.add(insertAt, CharCell(id, originLeft, ch))
        }

        fun integrateDelete(targetId: OpId) {
            clock = maxOf(clock, targetId.lamport)
            sequence.firstOrNull { it.id == targetId }?.let { it.tombstone = true }
        }

        fun integrateMark(op: MarkOp) {
            clock = maxOf(clock, op.opId.lamport)
            markOps[op.opId] = op
        }

        fun mergeFrom(peer: RichDocumentReplica) {
            for (cell in peer.sequence) integrateInsert(cell.id, cell.originLeft, cell.ch)
            for (cell in peer.sequence) if (cell.tombstone) integrateDelete(cell.id)
            for (op in peer.markOps.values) integrateMark(op)
        }

        fun plainText(): String = buildString {
            for (c in sequence) if (!c.tombstone) append(c.ch)
        }

        fun visibleLength(): Int = sequence.count { !it.tombstone }

        private fun visibleCharIdAtOrNull(visibleIdx: Int): OpId? {
            if (visibleIdx < 0) return null
            var seen = 0
            for (c in sequence) {
                if (!c.tombstone) {
                    if (seen == visibleIdx) return c.id
                    seen++
                }
            }
            return null
        }

        fun resolveRuns(): List<ResolvedRun> {
            if (sequence.isEmpty()) return emptyList()
            val idxById = HashMap<OpId, Int>(sequence.size)
            for ((i, c) in sequence.withIndex()) idxById[c.id] = i

            val sortedOps = markOps.values.sortedBy { it.opId }
            val perCellMarks = Array(sequence.size) { LinkedHashMap<MarkKind, String?>() }
            for (op in sortedOps) {
                val rawS = idxById[op.start.charId] ?: continue
                val rawE = idxById[op.end.charId] ?: continue
                // Never invert span boundaries even if concurrent operations reordered references
                val lo = minOf(rawS, rawE)
                var hi = maxOf(rawS, rawE)
                if (op.end.side == AnchorSide.AFTER) {
                    while (hi + 1 < sequence.size && sequence[hi + 1].originLeft == sequence[maxOf(rawS, rawE)].id) {
                        hi++
                    }
                }
                for (i in lo..hi) {
                    if (op.action == MarkAction.ADD) perCellMarks[i][op.kind] = op.payload
                    else perCellMarks[i].remove(op.kind)
                }
            }

            val runs = ArrayList<ResolvedRun>()
            var sb = StringBuilder()
            var curMarks: Set<MarkKind>? = null
            var curAttrs: Map<MarkKind, String>? = null
            for (i in sequence.indices) {
                if (sequence[i].tombstone) continue
                val mSet = perCellMarks[i].keys.toSet()
                val attrs = perCellMarks[i].mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
                if (curMarks != null && (mSet != curMarks || attrs != curAttrs)) {
                    runs.add(ResolvedRun(sb.toString(), curMarks, curAttrs!!))
                    sb = StringBuilder()
                }
                curMarks = mSet
                curAttrs = attrs
                sb.append(sequence[i].ch)
            }
            if (sb.isNotEmpty() && curMarks != null) {
                runs.add(ResolvedRun(sb.toString(), curMarks, curAttrs!!))
            }
            return runs
        }
    }
}
