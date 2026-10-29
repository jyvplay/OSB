// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

import kotlin.math.max
import kotlin.math.min

/**
 * Clean-room Myers $O((N+M)D)$ line diff, unified patch formatter, and 3-way merge engine (`DocumentDiffMerge`).
 *
 * Powers Writer track-changes comparison and offline local-first conflict resolution without Git CLI:
 * - Myers (1986) shortest edit script (`diffLines(a, b)`) with bounded edit distance (`MAX_LINES = 8192`).
 * - Unified diff formatter (`formatUnifiedDiff`) with configurable context window.
 * - Deterministic 3-way merge (`merge3(base, ours, theirs)`):
 *   - Cleanly merges non-overlapping edits and identical concurrent edits (`ours == theirs`).
 *   - Emits standard `<<<<<<< OURS` / `=======` / `>>>>>>> THEIRS` conflict blocks with exact `conflictCount`
 *     whenever `ours` and `theirs` make divergent overlapping modifications to `base`.
 */
object DocumentDiffMerge {
    const val SCOPE =
        "Myers O((N+M)D) shortest-edit-script line diff + unified diff formatter + 3-way merge with conflict markers (cap 8,192 lines); word-level fuzzy move detection out of scope."

    const val MAX_LINES = 8_192

    enum class OpKind { EQUAL, DELETE, INSERT }

    data class EditOp(val kind: OpKind, val line: String)

    data class MergeResult(
        val lines: List<String>,
        val conflictCount: Int,
    ) {
        val isClean: Boolean get() = conflictCount == 0
        val text: String get() = lines.joinToString("\n")
    }

    fun diffLines(before: List<String>, after: List<String>): List<EditOp> {
        require(before.size <= MAX_LINES && after.size <= MAX_LINES) {
            "Input exceeds MAX_LINES ($MAX_LINES)"
        }
        val n = before.size
        val m = after.size
        if (n == 0 && m == 0) return emptyList()
        if (n == 0) return after.map { EditOp(OpKind.INSERT, it) }
        if (m == 0) return before.map { EditOp(OpKind.DELETE, it) }

        val maxD = n + m
        val offset = maxD
        var v = IntArray(2 * maxD + 1)
        val trace = ArrayList<IntArray>()

        outer@ for (d in 0..maxD) {
            trace.add(v.copyOf())
            val nextV = v.copyOf()
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    v[offset + k + 1]
                } else {
                    v[offset + k - 1] + 1
                }
                var y = x - k
                while (x < n && y < m && before[x] == after[y]) {
                    x++
                    y++
                }
                nextV[offset + k] = x
                if (x >= n && y >= m) {
                    v = nextV
                    trace.add(v.copyOf())
                    break@outer
                }
                k += 2
            }
            v = nextV
        }

        // Backtrack trace from (n, m) to (0, 0).
        val rev = ArrayList<EditOp>(n + m)
        var x = n
        var y = m
        for (d in trace.size - 2 downTo 0) {
            val vd = trace[d]
            val k = x - y
            val prevK = if (k == -d || (k != d && vd[offset + k - 1] < vd[offset + k + 1])) {
                k + 1
            } else {
                k - 1
            }
            val prevX = vd[offset + prevK]
            val prevY = prevX - prevK
            while (x > prevX && y > prevY) {
                rev.add(EditOp(OpKind.EQUAL, before[x - 1]))
                x--
                y--
            }
            if (d > 0) {
                if (x == prevX) {
                    rev.add(EditOp(OpKind.INSERT, after[y - 1]))
                    y--
                } else {
                    rev.add(EditOp(OpKind.DELETE, before[x - 1]))
                    x--
                }
            }
        }
        rev.reverse()
        return rev
    }

    fun formatUnifiedDiff(
        before: List<String>,
        after: List<String>,
        fromLabel: String = "a/doc",
        toLabel: String = "b/doc",
        context: Int = 2,
    ): String {
        val ops = diffLines(before, after)
        val changedIdx = ops.indices.filter { ops[it].kind != OpKind.EQUAL }
        if (changedIdx.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("--- ").append(fromLabel).append('\n')
        sb.append("+++ ").append(toLabel).append('\n')
        val start = max(0, changedIdx.first() - context)
        val end = min(ops.lastIndex, changedIdx.last() + context)
        for (i in start..end) {
            val op = ops[i]
            val prefix = when (op.kind) {
                OpKind.EQUAL -> ' '
                OpKind.DELETE -> '-'
                OpKind.INSERT -> '+'
            }
            sb.append(prefix).append(op.line).append('\n')
        }
        return sb.toString()
    }

    private data class BaseEdit(val baseStart: Int, val baseEnd: Int, val replacement: List<String>)

    private fun extractBaseEdits(base: List<String>, target: List<String>): List<BaseEdit> {
        val ops = diffLines(base, target)
        val edits = mutableListOf<BaseEdit>()
        var baseIdx = 0
        var i = 0
        while (i < ops.size) {
            if (ops[i].kind == OpKind.EQUAL) {
                baseIdx++
                i++
            } else {
                val start = baseIdx
                val repl = mutableListOf<String>()
                while (i < ops.size && ops[i].kind != OpKind.EQUAL) {
                    if (ops[i].kind == OpKind.DELETE) baseIdx++
                    else repl.add(ops[i].line)
                    i++
                }
                edits.add(BaseEdit(start, baseIdx, repl))
            }
        }
        return edits
    }

    fun merge3(
        base: List<String>,
        ours: List<String>,
        theirs: List<String>,
        oursLabel: String = "OURS",
        theirsLabel: String = "THEIRS",
    ): MergeResult {
        val ourEdits = extractBaseEdits(base, ours)
        val theirEdits = extractBaseEdits(base, theirs)
        val out = mutableListOf<String>()
        var conflicts = 0
        var bCursor = 0
        var oi = 0
        var ti = 0

        fun overlaps(a: BaseEdit, b: BaseEdit): Boolean {
            if (a.baseStart == a.baseEnd && b.baseStart == b.baseEnd) return a.baseStart == b.baseStart
            return max(a.baseStart, b.baseStart) < min(a.baseEnd, b.baseEnd) ||
                (a.baseStart == a.baseEnd && a.baseStart in b.baseStart..b.baseEnd) ||
                (b.baseStart == b.baseEnd && b.baseStart in a.baseStart..a.baseEnd)
        }

        while (oi < ourEdits.size || ti < theirEdits.size) {
            val nextO = ourEdits.getOrNull(oi)
            val nextT = theirEdits.getOrNull(ti)
            when {
                nextT == null || (nextO != null && nextO.baseEnd < nextT.baseStart && !overlaps(nextO, nextT)) -> {
                    while (bCursor < nextO!!.baseStart) out.add(base[bCursor++])
                    out.addAll(nextO.replacement)
                    bCursor = nextO.baseEnd
                    oi++
                }
                nextO == null || (nextT.baseEnd < nextO.baseStart && !overlaps(nextO, nextT)) -> {
                    while (bCursor < nextT.baseStart) out.add(base[bCursor++])
                    out.addAll(nextT.replacement)
                    bCursor = nextT.baseEnd
                    ti++
                }
                else -> {
                    // Expand transitive overlap cluster across ourEdits and theirEdits.
                    var clusterStart = min(nextO.baseStart, nextT.baseStart)
                    var clusterEnd = max(nextO.baseEnd, nextT.baseEnd)
                    val oStart = oi
                    val tStart = ti
                    oi++
                    ti++
                    var grown = true
                    while (grown) {
                        grown = false
                        while (oi < ourEdits.size && ourEdits[oi].baseStart <= clusterEnd &&
                            (ourEdits[oi].baseStart < clusterEnd || clusterStart == clusterEnd)
                        ) {
                            clusterEnd = max(clusterEnd, ourEdits[oi].baseEnd)
                            oi++
                            grown = true
                        }
                        while (ti < theirEdits.size && theirEdits[ti].baseStart <= clusterEnd &&
                            (theirEdits[ti].baseStart < clusterEnd || clusterStart == clusterEnd)
                        ) {
                            clusterEnd = max(clusterEnd, theirEdits[ti].baseEnd)
                            ti++
                            grown = true
                        }
                    }
                    while (bCursor < clusterStart) out.add(base[bCursor++])
                    val ourSlice = applyEditsToRegion(base, clusterStart, clusterEnd, ourEdits.subList(oStart, oi))
                    val theirSlice = applyEditsToRegion(base, clusterStart, clusterEnd, theirEdits.subList(tStart, ti))
                    if (ourSlice == theirSlice) {
                        out.addAll(ourSlice)
                    } else {
                        conflicts++
                        out.add("<<<<<<< $oursLabel")
                        out.addAll(ourSlice)
                        out.add("=======")
                        out.addAll(theirSlice)
                        out.add(">>>>>>> $theirsLabel")
                    }
                    bCursor = clusterEnd
                }
            }
        }
        while (bCursor < base.size) out.add(base[bCursor++])
        return MergeResult(out, conflicts)
    }

    private fun applyEditsToRegion(
        base: List<String>,
        start: Int,
        end: Int,
        edits: List<BaseEdit>,
    ): List<String> {
        val res = mutableListOf<String>()
        var cur = start
        for (e in edits) {
            while (cur < e.baseStart) res.add(base[cur++])
            res.addAll(e.replacement)
            cur = e.baseEnd
        }
        while (cur < end) res.add(base[cur++])
        return res
    }
}
