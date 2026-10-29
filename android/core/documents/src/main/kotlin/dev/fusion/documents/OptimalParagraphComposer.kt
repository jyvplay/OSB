// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * R54 clean-room **Optimal Paragraph Composer** — a three-pass Knuth–Plass dynamic program with the
 * adversarial guards that the simpler R49 `KnuthPlassLineBreaker` does not carry:
 *
 * 1. **No inverted slices**: candidate breaks where `end < start` (produced by consecutive negative
 *    penalties) are skipped, never rendered (`storytold/craft-libs#19`).
 * 2. **Hard hyphen cap**: more than `maxConsecutiveHyphens` (default 2) flagged breaks is refused even in
 *    the emergency-stretch pass, before any greedy live-typing fallback is taken.
 * 3. **Trailing-forced-penalty line is never stretched** (`ratio = 0` for the last line).
 * 4. **Pass ladder**: `preTolerance` without hyphens → `tolerance` with hyphens → `MAX_BADNESS` with
 *    emergency stretch → deterministic greedy wrap, each reported explicitly via [BreakResult.passUsed].
 *
 * This is *additive*: it composes with (and does not replace) `KnuthPlassLineBreaker`, which remains the
 * interactive-editor breaker.
 */
object OptimalParagraphComposer {
    const val INF_PENALTY = 10_000
    const val MAX_BADNESS = 10_000

    sealed interface Item {
        data class Box(val width: Double, val text: String) : Item
        data class Glue(val width: Double, val stretch: Double, val shrink: Double) : Item
        data class Penalty(val width: Double, val penalty: Int, val flagged: Boolean = false) : Item
    }

    data class Options(
        val preTolerance: Int = 100,
        val tolerance: Int = 200,
        val emergencyStretch: Double = 24.0,
        val linePenalty: Int = 10,
        val flaggedDemerit: Long = 3_000L,
        val fitnessDemerit: Long = 3_000L,
        val maxConsecutiveHyphens: Int = 2,
    )

    data class Line(
        val index: Int,
        val startItem: Int,
        val endItem: Int,
        val naturalWidth: Double,
        val measure: Double,
        val adjustmentRatio: Double,
        val badness: Int,
        val fitnessClass: Int,
        val endsWithHyphen: Boolean,
        val text: String,
    )

    data class BreakResult(
        val lines: List<Line>,
        val totalDemerits: Long,
        val usedGreedyFallback: Boolean,
        val passUsed: Int,
    )

    fun tokenizeParagraph(
        words: List<String>,
        measureText: (String) -> Double,
        spaceWidth: Double,
        hyphenWidth: Double,
        hyphenator: ((String) -> List<Int>)? = null,
    ): List<Item> {
        val items = ArrayList<Item>()
        for ((wIdx, word) in words.withIndex()) {
            if (word.isEmpty()) continue
            val breakPoints = hyphenator?.invoke(word)?.filter { it in 1 until word.length }?.distinct()?.sorted().orEmpty()
            if (breakPoints.isEmpty()) {
                items.add(Item.Box(measureText(word), word))
            } else {
                var prev = 0
                for (bp in breakPoints) {
                    items.add(Item.Box(measureText(word.substring(prev, bp)), word.substring(prev, bp)))
                    items.add(Item.Penalty(hyphenWidth, 50, flagged = true))
                    prev = bp
                }
                items.add(Item.Box(measureText(word.substring(prev)), word.substring(prev)))
            }
            if (wIdx < words.lastIndex) {
                items.add(Item.Glue(spaceWidth, spaceWidth * 0.5, spaceWidth / 3.0))
            }
        }
        items.add(Item.Penalty(0.0, INF_PENALTY, flagged = false))
        items.add(Item.Glue(0.0, 1_000_000.0, 0.0))
        items.add(Item.Penalty(0.0, -INF_PENALTY, flagged = false))
        return items
    }

    fun compose(items: List<Item>, lineMeasures: (Int) -> Double, options: Options = Options()): BreakResult {
        require(lineMeasures(0) > 0.0) { "line measure must be positive" }
        if (items.isEmpty()) return BreakResult(emptyList(), 0L, usedGreedyFallback = false, passUsed = 1)
        solve(items, lineMeasures, options.preTolerance, 0.0, allowFlagged = false, options)?.let {
            return it.copy(passUsed = 1)
        }
        solve(items, lineMeasures, options.tolerance, 0.0, allowFlagged = true, options)?.let {
            return it.copy(passUsed = 2)
        }
        solve(items, lineMeasures, MAX_BADNESS, options.emergencyStretch, allowFlagged = true, options)?.let {
            return it.copy(passUsed = 3)
        }
        return greedy(items, lineMeasures)
    }

    private fun fitnessOf(ratio: Double): Int = when {
        ratio < -0.5 -> 0
        ratio <= 0.5 -> 1
        ratio <= 1.0 -> 2
        else -> 3
    }

    private fun badnessOf(ratio: Double): Int {
        if (ratio < -1.0) return MAX_BADNESS + 1
        val cubic = 100.0 * abs(ratio) * abs(ratio) * abs(ratio)
        return min(MAX_BADNESS.toLong(), floor(cubic + 0.5).toLong()).toInt()
    }

    private fun isLegalBreakpoint(items: List<Item>, i: Int, allowFlagged: Boolean): Boolean = when (val it = items[i]) {
        is Item.Penalty -> it.penalty < INF_PENALTY && (allowFlagged || !it.flagged)
        is Item.Glue -> i > 0 && items[i - 1] is Item.Box
        is Item.Box -> false
    }

    private data class Node(
        val index: Int,
        val line: Int,
        val fitness: Int,
        val widthAfter: Double,
        val stretchAfter: Double,
        val shrinkAfter: Double,
        val demerits: Long,
        val hyphens: Int,
        val prev: Node?,
        val ratio: Double,
        val badness: Int,
    )

    private fun firstItemAfter(items: List<Item>, breakIdx: Int): Int {
        var k = breakIdx
        if (k < items.size && items[k] is Item.Penalty) k++
        while (k < items.size && items[k] is Item.Glue) k++
        return k
    }

    private fun solve(
        items: List<Item>,
        lineMeasures: (Int) -> Double,
        maxBadness: Int,
        extraStretch: Double,
        allowFlagged: Boolean,
        options: Options,
    ): BreakResult? {
        val n = items.size
        val cumW = DoubleArray(n + 1)
        val cumS = DoubleArray(n + 1)
        val cumK = DoubleArray(n + 1)
        for (i in 0 until n) {
            val w = (items[i] as? Item.Box)?.width ?: (items[i] as? Item.Glue)?.width ?: 0.0
            cumW[i + 1] = cumW[i] + w
            cumS[i + 1] = cumS[i] + ((items[i] as? Item.Glue)?.stretch ?: 0.0)
            cumK[i + 1] = cumK[i] + ((items[i] as? Item.Glue)?.shrink ?: 0.0)
        }

        val active = ArrayList<Node>()
        active.add(Node(firstItemAfter(items, 0), 0, 1, 0.0, 0.0, 0.0, 0L, 0, null, 0.0, 0))

        for (b in 0 until n) {
            if (!isLegalBreakpoint(items, b, allowFlagged)) continue
            val pen = items[b] as? Item.Penalty
            val penWidth = pen?.width ?: 0.0
            val penValue = pen?.penalty ?: 0
            val penFlagged = pen?.flagged ?: false
            val forced = penValue <= -INF_PENALTY
            val best = arrayOfNulls<Node>(4)
            val dead = ArrayList<Node>()

            for (a in active) {
                val startIdx = a.index
                // Adversarial guard: never render an inverted slice.
                if (b < startIdx) continue
                if (penFlagged && a.hyphens + 1 > options.maxConsecutiveHyphens) continue

                val measure = lineMeasures(a.line)
                val lineW = (cumW[b] - cumW[startIdx]) + penWidth
                val lineS = (cumS[b] - cumS[startIdx]) + extraStretch
                val lineK = cumK[b] - cumK[startIdx]
                val ratio = when {
                    forced && lineW <= measure -> 0.0
                    abs(measure - lineW) <= 1e-9 -> 0.0
                    lineW < measure -> if (lineS > 0.0) (measure - lineW) / lineS else 1e9
                    else -> if (lineK > 0.0) (measure - lineW) / lineK else -2.0
                }
                if (ratio < -1.0) {
                    dead.add(a)
                    if (!forced) continue
                }
                val badness = if (forced && ratio >= -1.0) 0 else badnessOf(ratio)
                if (badness > maxBadness && !forced) continue

                val fitness = fitnessOf(ratio)
                val base = options.linePenalty.toLong() + badness.toLong()
                var demerits: Long = when {
                    forced -> base * base
                    penValue >= 0 -> (base + penValue) * (base + penValue)
                    else -> base * base - penValue.toLong() * penValue.toLong()
                }
                if (penFlagged && a.hyphens >= 1) demerits += options.flaggedDemerit
                if (abs(fitness - a.fitness) > 1) demerits += options.fitnessDemerit

                val nextIdx = firstItemAfter(items, b)
                val candidate = Node(
                    index = nextIdx,
                    line = a.line + 1,
                    fitness = fitness,
                    widthAfter = cumW[nextIdx],
                    stretchAfter = cumS[nextIdx],
                    shrinkAfter = cumK[nextIdx],
                    demerits = a.demerits + demerits,
                    hyphens = if (penFlagged) a.hyphens + 1 else 0,
                    prev = a,
                    ratio = if (forced) 0.0 else ratio.coerceIn(-1.0, 10.0),
                    badness = badness,
                )
                val slot = best[fitness]
                if (slot == null || candidate.demerits < slot.demerits) best[fitness] = candidate
            }
            active.removeAll(dead.toSet())
            for (c in best) if (c != null) active.add(c)
            if (active.isEmpty()) return null
            if (forced) {
                // A forced break ends the paragraph: keep only terminal candidates.
                val terminal = active.filter { it.index >= n }
                if (terminal.isNotEmpty()) active.retainAll(terminal.toSet())
            }
        }

        val winner = active.filter { it.index >= n }.minByOrNull { it.demerits } ?: return null
        val chain = ArrayList<Node>()
        var cur: Node? = winner
        while (cur != null && cur.prev != null) {
            chain.add(cur)
            cur = cur.prev
        }
        chain.reverse()
        if (chain.isEmpty()) return null

        val lines = ArrayList<Line>(chain.size)
        var sliceStart = 0
        for ((idx, node) in chain.withIndex()) {
            val sliceEnd = node.prev!!.index.let { prevIdx ->
                // `b` = last item index consumed by this line = one before the next line's start.
                maxOf(prevIdx, sliceStart) - 1
            }
            val end = sliceEnd.coerceAtLeast(sliceStart)
            val sb = StringBuilder()
            var natural = 0.0
            for (k in sliceStart..min(end, items.lastIndex)) {
                when (val it = items[k]) {
                    is Item.Box -> { sb.append(it.text); natural += it.width }
                    is Item.Glue -> if (k < end) { sb.append(' '); natural += it.width }
                    is Item.Penalty -> if (k == end && it.flagged) { sb.append('-'); natural += it.width }
                }
            }
            lines.add(
                Line(
                    index = idx,
                    startItem = sliceStart,
                    endItem = end,
                    naturalWidth = natural,
                    measure = lineMeasures(idx),
                    adjustmentRatio = node.ratio,
                    badness = node.badness,
                    fitnessClass = node.fitness,
                    endsWithHyphen = (items.getOrNull(end) as? Item.Penalty)?.flagged == true,
                    text = sb.toString().trimEnd(),
                ),
            )
            sliceStart = end + 1
        }
        return BreakResult(lines, winner.demerits, usedGreedyFallback = false, passUsed = 1)
    }

    private fun greedy(items: List<Item>, lineMeasures: (Int) -> Double): BreakResult {
        val lines = ArrayList<Line>()
        var lineIdx = 0
        var start = 0
        var width = 0.0
        var pendingSpace = 0.0
        val sb = StringBuilder()
        for (i in items.indices) {
            val measure = lineMeasures(lineIdx)
            when (val it = items[i]) {
                is Item.Box -> {
                    val needed = (if (sb.isEmpty()) 0.0 else pendingSpace) + it.width
                    if (sb.isNotEmpty() && width + needed > measure) {
                        lines.add(Line(lineIdx, start, i - 1, width, measure, 0.0, MAX_BADNESS, 1, false, sb.toString()))
                        lineIdx++
                        start = i
                        sb.setLength(0)
                        width = 0.0
                    }
                    if (sb.isNotEmpty() && pendingSpace > 0.0) {
                        sb.append(' ')
                        width += pendingSpace
                    }
                    sb.append(it.text)
                    width += it.width
                    pendingSpace = 0.0
                }
                is Item.Glue -> if (it.width > 0.0) pendingSpace = it.width
                is Item.Penalty -> {}
            }
        }
        if (sb.isNotEmpty()) {
            lines.add(Line(lineIdx, start, items.lastIndex, width, lineMeasures(lineIdx), 0.0, 0, 1, false, sb.toString()))
        }
        return BreakResult(lines, MAX_BADNESS.toLong() * lines.size, usedGreedyFallback = true, passUsed = 4)
    }
}
