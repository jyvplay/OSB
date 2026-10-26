// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import java.util.Collections

/**
 * Word-level diff over caller token lists.
 *
 * Sits next to [LineDiff] (whole-line granularity): when a single long line
 * changes by one word, a line diff reports the entire line rewritten while
 * this reports the word. Tokenization is caller duty — the caller splits on
 * whatever word boundary fits its text, so this kernel never has to guess
 * whether `don't` is one word or two. Comparison is the same bounded-LCS
 * family as [LineDiff] (interned identities, capped matrix, identical
 * prefer-REMOVE walk order) but standalone: no text splitting, no TextBytes
 * dependency, lists in and ops out. Each side is capped at 2048 tokens with
 * a 1M-cell matrix budget, mirroring the line kernel's bounds.
 *
 * Plain JVM Kotlin (java.util only), unit-testable on the JVM.
 */
object WordDiff {
    const val MAX_WORDS = 2048
    const val MAX_CELLS = 1_000_000

    enum class Op { SAME, REMOVED, ADDED }
    data class WordOp(val op: Op, val word: String)
    data class Comparison(val ops: List<WordOp>, val added: Int, val removed: Int)

    /**
     * Diffs token list [a] against [b].
     *
     * @throws IllegalArgumentException when either side exceeds the token
     * budget or the matrix budget is exceeded.
     */
    fun compare(a: List<String>, b: List<String>): Comparison {
        require(a.size <= MAX_WORDS && b.size <= MAX_WORDS) { "word budget exceeded" }
        val cells = (a.size + 1L) * (b.size + 1L)
        require(cells <= MAX_CELLS) { "matrix budget exceeded; narrow the comparison" }
        val names = HashMap<String, Int>()
        fun ids(input: List<String>) = input.map { w -> names.getOrPut(w) { names.size } }
        val ai = ids(a)
        val bi = ids(b)
        val stride = b.size + 1
        val lengths = IntArray(cells.toInt())
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lengths[i * stride + j] = if (ai[i] == bi[j]) 1 + lengths[(i + 1) * stride + j + 1]
            else maxOf(lengths[(i + 1) * stride + j], lengths[i * stride + j + 1])
        }
        val ops = ArrayList<WordOp>()
        var i = 0
        var j = 0
        var added = 0
        var removed = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && ai[i] == bi[j] -> {
                    ops.add(WordOp(Op.SAME, a[i++]))
                    j++
                }
                i < a.size && (j == b.size || lengths[(i + 1) * stride + j] >= lengths[i * stride + j + 1]) -> {
                    ops.add(WordOp(Op.REMOVED, a[i++]))
                    removed++
                }
                else -> {
                    ops.add(WordOp(Op.ADDED, b[j++]))
                    added++
                }
            }
        }
        return Comparison(Collections.unmodifiableList(ops), added, removed)
    }
}
