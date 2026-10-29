// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import kotlin.math.abs
import kotlin.math.pow

/**
 * Clean-room Knuth–Plass (1981) optimal dynamic-programming paragraph line-breaking engine
 * over boxes, stretch/shrink glue, and hyphenation/explicit penalties.
 *
 * Minimizes total paragraph demerits across all feasible line breaks rather than greedy first-fit:
 * - Adjustment ratio `r = (lineWidth - naturalWidth) / stretch` (if `naturalWidth < lineWidth`)
 *   or `(lineWidth - naturalWidth) / shrink` (if `naturalWidth > lineWidth`).
 * - Line badness `b = min(10_000, 100 * |r|^3)`.
 * - Line demerits `d = (linePenalty + b + max(0, penalty))^2` (or `(linePenalty + b)^2 - penalty^2`
 *   when `penalty < 0`), plus `flaggedDemerits` for consecutive hyphenated lines and
 *   `fitnessDemerits` when adjacent lines jump more than 1 tightness fitness class (`0..3`).
 */
object KnuthPlassLineBreaker {
    const val INF_PENALTY = 10_000.0
    const val FORCED_BREAK_PENALTY = -10_000.0
    const val MAX_ITEMS = 8_192

    sealed interface Item {
        data class Box(val width: Double, val text: String) : Item {
            init {
                require(width >= 0.0 && width.isFinite()) { "invalid box width $width" }
            }
        }

        data class Glue(val width: Double, val stretch: Double, val shrink: Double) : Item {
            init {
                require(width >= 0.0 && stretch >= 0.0 && shrink >= 0.0) { "invalid glue metrics" }
                require(width.isFinite() && stretch.isFinite() && shrink.isFinite()) { "non-finite glue" }
            }
        }

        data class Penalty(val width: Double, val penalty: Double, val flagged: Boolean = false) : Item {
            init {
                require(width >= 0.0 && width.isFinite() && penalty.isFinite()) { "invalid penalty item" }
            }
        }
    }

    data class Config(
        val lineWidth: Double,
        val tolerance: Double = 2.5,
        val linePenalty: Double = 10.0,
        val flaggedDemerits: Double = 3_000.0,
        val fitnessDemerits: Double = 3_000.0,
    ) {
        init {
            require(lineWidth > 0.0 && lineWidth.isFinite()) { "lineWidth must be > 0" }
            require(tolerance >= 1.0 && tolerance.isFinite()) { "tolerance must be >= 1.0" }
        }
    }

    data class BrokenLine(
        val lineIndex: Int,
        val text: String,
        val naturalWidth: Double,
        val adjustmentRatio: Double,
        val fitnessClass: Int,
        val hyphenatedEnd: Boolean,
    )

    data class LayoutResult(
        val lines: List<BrokenLine>,
        val totalDemerits: Double,
        val usedFallbackGreedy: Boolean,
    )

    fun tokenizeParagraph(
        text: String,
        glyphWidth: Double = 7.0,
        spaceWidth: Double = 5.0,
        spaceStretch: Double = 3.5,
        spaceShrink: Double = 1.8,
        hyphenator: ((String) -> List<String>)? = null,
    ): List<Item> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val items = ArrayList<Item>()
        for ((wi, word) in words.withIndex()) {
            val syllables = hyphenator?.invoke(word)?.takeIf { it.size > 1 } ?: listOf(word)
            for ((si, syl) in syllables.withIndex()) {
                items.add(Item.Box(syl.length * glyphWidth, syl))
                if (si + 1 < syllables.size) {
                    items.add(Item.Penalty(glyphWidth, 50.0, flagged = true))
                }
            }
            if (wi + 1 < words.size) {
                items.add(Item.Glue(spaceWidth, spaceStretch, spaceShrink))
            }
        }
        // Standard Knuth-Plass (1981, p.1126) finishing sequence: Penalty(0, +INF), Glue(0, +inf, 0), Penalty(0, -INF)
        items.add(Item.Penalty(0.0, INF_PENALTY, flagged = false))
        items.add(Item.Glue(0.0, 1_000_000.0, 0.0))
        items.add(Item.Penalty(0.0, FORCED_BREAK_PENALTY, flagged = false))
        require(items.size <= MAX_ITEMS) { "paragraph item stream exceeds $MAX_ITEMS" }
        return items
    }

    fun breakParagraph(items: List<Item>, config: Config): LayoutResult {
        if (items.isEmpty()) return LayoutResult(emptyList(), 0.0, false)
        require(items.size <= MAX_ITEMS) { "item count ${items.size} exceeds $MAX_ITEMS" }

        val n = items.size
        val sumW = DoubleArray(n + 1)
        val sumY = DoubleArray(n + 1)
        val sumZ = DoubleArray(n + 1)
        for (i in 0 until n) {
            sumW[i + 1] = sumW[i]
            sumY[i + 1] = sumY[i]
            sumZ[i + 1] = sumZ[i]
            when (val it = items[i]) {
                is Item.Box -> sumW[i + 1] += it.width
                is Item.Glue -> {
                    sumW[i + 1] += it.width
                    sumY[i + 1] += it.stretch
                    sumZ[i + 1] += it.shrink
                }
                is Item.Penalty -> {}
            }
        }

        fun isLegalBreak(idx: Int): Boolean {
            if (idx == 0) return true
            if (idx !in 0 until n) return false
            return when (val it = items[idx]) {
                is Item.Penalty -> it.penalty < INF_PENALTY
                is Item.Glue -> idx > 0 && items[idx - 1] is Item.Box
                is Item.Box -> false
            }
        }

        fun skipLeadingGlue(fromBreak: Int): Int {
            var k = if (fromBreak == 0) 0 else fromBreak + 1
            while (k < n) {
                val it = items[k]
                if (it is Item.Glue || (it is Item.Penalty && it.penalty >= INF_PENALTY && it.width == 0.0)) k++
                else break
            }
            return k
        }

        data class Node(
            val index: Int,
            val line: Int,
            val fitness: Int,
            val demerits: Double,
            val ratio: Double,
            val naturalWidth: Double,
            val prev: Node?,
        )

        var active = mutableListOf(Node(0, 0, 1, 0.0, 0.0, 0.0, null))
        for (b in 0 until n) {
            if (!isLegalBreak(b) || b == 0) continue
            val penItem = items[b] as? Item.Penalty
            val penVal = penItem?.penalty ?: 0.0
            val penWidth = penItem?.width ?: 0.0
            val penFlag = penItem?.flagged ?: false

            val bestByFitness = arrayOfNulls<Node>(4)
            val survivors = ArrayList<Node>(active.size)

            for (a in active) {
                val start = skipLeadingGlue(a.index)
                if (start > b) {
                    survivors.add(a)
                    continue
                }
                val w = (sumW[b] - sumW[start]) + penWidth
                val y = sumY[b] - sumY[start]
                val z = sumZ[b] - sumZ[start]
                val delta = config.lineWidth - w
                val r = when {
                    abs(delta) < 1e-9 -> 0.0
                    delta > 0.0 -> if (y > 0.0) delta / y else Double.POSITIVE_INFINITY
                    else -> if (z > 0.0) delta / z else Double.NEGATIVE_INFINITY
                }

                if (r > -1.0 && penVal > FORCED_BREAK_PENALTY) {
                    survivors.add(a)
                }
                if (r >= -1.0 && (r <= config.tolerance || penVal <= FORCED_BREAK_PENALTY)) {
                    val clampedR = r.coerceIn(-1.0, 100.0)
                    val badness = if (!r.isFinite()) 10_000.0 else minOf(10_000.0, 100.0 * abs(clampedR).pow(3.0))
                    var dem = when {
                        penVal >= 0.0 -> (config.linePenalty + badness + penVal).pow(2.0)
                        penVal > FORCED_BREAK_PENALTY -> (config.linePenalty + badness).pow(2.0) - penVal.pow(2.0)
                        else -> (config.linePenalty + badness).pow(2.0)
                    }
                    val prevFlag = a.index > 0 && (items[a.index] as? Item.Penalty)?.flagged == true
                    if (penFlag && prevFlag) dem += config.flaggedDemerits
                    val fit = when {
                        clampedR < -0.5 -> 0
                        clampedR <= 0.5 -> 1
                        clampedR <= 1.0 -> 2
                        else -> 3
                    }
                    if (abs(fit - a.fitness) > 1) dem += config.fitnessDemerits
                    val total = a.demerits + dem
                    val cur = bestByFitness[fit]
                    if (cur == null || total < cur.demerits) {
                        bestByFitness[fit] = Node(b, a.line + 1, fit, total, clampedR, w, a)
                    }
                }
            }

            active = survivors
            for (cand in bestByFitness) if (cand != null) active.add(cand)
            if (active.isEmpty() && b < n - 1) break
        }

        val terminal = active.filter { it.index == n - 1 }.minByOrNull { it.demerits }
        if (terminal == null) {
            return greedyFallback(items, config)
        }

        val chain = ArrayList<Node>()
        var cur: Node? = terminal
        while (cur != null && cur.prev != null) {
            chain.add(cur)
            cur = cur.prev
        }
        chain.reverse()

        val rendered = chain.mapIndexed { idx, node ->
            val from = skipLeadingGlue(node.prev!!.index)
            val sb = StringBuilder()
            for (k in from until node.index) {
                when (val it = items[k]) {
                    is Item.Box -> sb.append(it.text)
                    is Item.Glue -> if (it.width > 0.0) sb.append(' ')
                    is Item.Penalty -> {}
                }
            }
            val endPen = items[node.index] as? Item.Penalty
            val hyph = endPen != null && endPen.flagged && endPen.width > 0.0
            if (hyph) sb.append('-')
            BrokenLine(
                lineIndex = idx,
                text = sb.toString().trimEnd(),
                naturalWidth = node.naturalWidth,
                adjustmentRatio = node.ratio,
                fitnessClass = node.fitness,
                hyphenatedEnd = hyph,
            )
        }
        return LayoutResult(rendered, terminal.demerits, usedFallbackGreedy = false)
    }

    private fun greedyFallback(items: List<Item>, config: Config): LayoutResult {
        val lines = ArrayList<BrokenLine>()
        val cur = StringBuilder()
        var curW = 0.0
        for (it in items) {
            when (it) {
                is Item.Box -> {
                    val spaceNeeded = if (cur.isEmpty()) 0.0 else 5.0
                    if (cur.isNotEmpty() && curW + spaceNeeded + it.width > config.lineWidth) {
                        lines.add(BrokenLine(lines.size, cur.toString(), curW, 0.0, 1, false))
                        cur.setLength(0)
                        cur.append(it.text)
                        curW = it.width
                    } else {
                        if (cur.isNotEmpty()) {
                            cur.append(' ')
                            curW += spaceNeeded
                        }
                        cur.append(it.text)
                        curW += it.width
                    }
                }
                else -> {}
            }
        }
        if (cur.isNotEmpty()) lines.add(BrokenLine(lines.size, cur.toString(), curW, 0.0, 1, false))
        return LayoutResult(lines, 1e9, usedFallbackGreedy = true)
    }
}
