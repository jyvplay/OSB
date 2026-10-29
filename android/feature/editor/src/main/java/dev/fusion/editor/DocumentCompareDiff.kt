// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Clean-room Myers $O(ND)$ word-token document redline compare engine for Writer.
 *
 * Compares an original revision and a revised revision at word/punctuation granularity and emits:
 * - Coalesced [DiffSpan] runs (`EQUAL`, `INSERT`, `DELETE`) suitable for `w:ins` / `w:del` or UI redlines.
 * - Dice/Jaccard token similarity score and net word delta statistics.
 */
object DocumentCompareDiff {
    const val MAX_TOKENS = 8_192

    enum class Op { EQUAL, INSERT, DELETE }

    data class DiffSpan(val op: Op, val text: String)

    data class CompareSummary(
        val spans: List<DiffSpan>,
        val insertedTokens: Int,
        val deletedTokens: Int,
        val unchangedTokens: Int,
        val diceSimilarity: Double,
    ) {
        fun toRedlineMarkup(): String = buildString {
            for (s in spans) {
                when (s.op) {
                    Op.EQUAL -> append(s.text)
                    Op.INSERT -> append("{+").append(s.text).append("+}")
                    Op.DELETE -> append("[-").append(s.text).append("-]")
                }
            }
        }
    }

    fun tokenizePreservingWhitespace(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val rx = Regex("\\s+|[A-Za-z0-9_]+|[^\\sA-Za-z0-9_]")
        for (m in rx.findAll(text)) out.add(m.value)
        require(out.size <= MAX_TOKENS) { "document token count ${out.size} exceeds $MAX_TOKENS" }
        return out
    }

    fun compare(original: String, revised: String): CompareSummary {
        val a = tokenizePreservingWhitespace(original)
        val b = tokenizePreservingWhitespace(revised)
        if (a.isEmpty() && b.isEmpty()) {
            return CompareSummary(emptyList(), 0, 0, 0, 1.0)
        }
        val rawOps = myersDiff(a, b)
        val coalesced = ArrayList<DiffSpan>()
        var insCnt = 0
        var delCnt = 0
        var eqCnt = 0
        for ((op, tok) in rawOps) {
            val isWord = tok.isNotBlank()
            when (op) {
                Op.INSERT -> if (isWord) insCnt++
                Op.DELETE -> if (isWord) delCnt++
                Op.EQUAL -> if (isWord) eqCnt++
            }
            val prev = coalesced.lastOrNull()
            if (prev != null && prev.op == op) {
                coalesced[coalesced.lastIndex] = DiffSpan(op, prev.text + tok)
            } else {
                coalesced.add(DiffSpan(op, tok))
            }
        }
        val denom = (2 * eqCnt + insCnt + delCnt)
        val dice = if (denom == 0) 1.0 else (2.0 * eqCnt) / denom
        return CompareSummary(coalesced, insCnt, delCnt, eqCnt, dice)
    }

    private fun myersDiff(a: List<String>, b: List<String>): List<Pair<Op, String>> {
        val n = a.size
        val m = b.size
        val max = n + m
        if (max == 0) return emptyList()
        val offset = max
        var v = IntArray(2 * max + 1)
        val trace = ArrayList<IntArray>()

        outer@ for (d in 0..max) {
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
                while (x < n && y < m && a[x] == b[y]) {
                    x++
                    y++
                }
                nextV[offset + k] = x
                if (x >= n && y >= m) {
                    v = nextV
                    break@outer
                }
                k += 2
            }
            v = nextV
        }

        // Backtrack shortest edit script
        val edits = ArrayList<Pair<Op, String>>()
        var x = n
        var y = m
        for (d in trace.lastIndex downTo 0) {
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
                edits.add(Op.EQUAL to a[x - 1])
                x--
                y--
            }
            if (d > 0) {
                if (x == prevX) {
                    edits.add(Op.INSERT to b[y - 1])
                    y--
                } else {
                    edits.add(Op.DELETE to a[x - 1])
                    x--
                }
            }
        }
        edits.reverse()
        return edits
    }
}
