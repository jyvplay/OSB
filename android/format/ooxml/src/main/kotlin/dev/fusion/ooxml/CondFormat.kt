// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import kotlin.math.roundToInt

/**
 * ECMA-376 SpreadsheetML Conditional Formatting rule evaluator (`<conditionalFormatting>`, §18.3.1.18).
 *
 * Evaluates prioritized `cfRule` rules (`priority` ascending, `stopIfTrue` respected) across
 * evaluated sheet cells:
 * - [Rule.CellIs] (`GT`, `GTE`, `LT`, `LTE`, `EQ`, `NEQ`, `BETWEEN`),
 * - [Rule.TextContains] (case-insensitive substring match),
 * - [Rule.TopN] (top/bottom `k` numeric values in target range),
 * - [Rule.ColorScale2] (linear RGB interpolation across range `[min, max]`),
 * - [Rule.DataBar] (normalized `[0.0, 1.0]` bar fill fraction across range `[min, max]`).
 */
object CondFormat {
    const val SCOPE =
        "ECMA-376 conditionalFormatting evaluator (CellIs, TextContains, TopN, 2-stop ColorScale RGB interpolation, DataBar fraction, priority + stopIfTrue); iconSet bitmaps and custom formula cfRules out of scope."

    enum class Operator { GT, GTE, LT, LTE, EQ, NEQ, BETWEEN }

    data class Rgb(val r: Int, val g: Int, val b: Int) {
        init {
            require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB ($r,$g,$b) outside 0..255" }
        }

        fun toHex(): String = "#%02X%02X%02X".format(r, g, b)
    }

    sealed interface Rule {
        val priority: Int
        val range: SheetRange.Range
        val stopIfTrue: Boolean

        data class CellIs(
            override val priority: Int,
            override val range: SheetRange.Range,
            val op: Operator,
            val threshold1: Double,
            val threshold2: Double = threshold1,
            val styleTag: String,
            val fill: Rgb,
            override val stopIfTrue: Boolean = true,
        ) : Rule

        data class TextContains(
            override val priority: Int,
            override val range: SheetRange.Range,
            val needle: String,
            val styleTag: String,
            val fill: Rgb,
            override val stopIfTrue: Boolean = true,
        ) : Rule {
            init {
                require(needle.isNotEmpty()) { "needle must not be empty" }
            }
        }

        data class TopN(
            override val priority: Int,
            override val range: SheetRange.Range,
            val count: Int,
            val bottom: Boolean = false,
            val styleTag: String,
            val fill: Rgb,
            override val stopIfTrue: Boolean = true,
        ) : Rule {
            init {
                require(count >= 1) { "TopN count must be >= 1" }
            }
        }

        data class ColorScale2(
            override val priority: Int,
            override val range: SheetRange.Range,
            val minColor: Rgb,
            val maxColor: Rgb,
            override val stopIfTrue: Boolean = false,
        ) : Rule

        data class DataBar(
            override val priority: Int,
            override val range: SheetRange.Range,
            val barColor: Rgb,
            override val stopIfTrue: Boolean = false,
        ) : Rule
    }

    data class CellDecoration(
        val styleTags: List<String>,
        val fill: Rgb?,
        val dataBarFraction: Double?,
    )

    fun evaluate(
        values: Map<String, FormulaEval.CellValue>,
        rules: List<Rule>,
    ): Map<String, CellDecoration> {
        require(rules.all { it.priority >= 1 }) { "cfRule priority must be >= 1" }
        val sortedRules = rules.sortedBy { it.priority }

        // Precompute numeric distributions per rule index for TopN / ColorScale / DataBar
        val rangeNums: List<List<Double>> = sortedRules.map { rule ->
            val list = ArrayList<Double>()
            for (r in rule.range.top.row..rule.range.bottom.row) {
                for (c in rule.range.top.col..rule.range.bottom.col) {
                    val v = values[SheetRange.Cell(c, r).toA1()]
                    if (v is FormulaEval.CellValue.Num && v.value.isFinite()) list.add(v.value)
                }
            }
            list
        }

        val stopped = HashSet<String>()
        val tagsByCell = LinkedHashMap<String, MutableList<String>>()
        val fillByCell = HashMap<String, Rgb>()
        val barByCell = HashMap<String, Double>()

        for ((idx, rule) in sortedRules.withIndex()) {
            val nums = rangeNums[idx]
            val minV = nums.minOrNull()
            val maxV = nums.maxOrNull()
            val topCutoff: Double? = if (rule is Rule.TopN && nums.isNotEmpty()) {
                val ordered = if (rule.bottom) nums.sorted() else nums.sortedDescending()
                ordered[(rule.count - 1).coerceAtMost(ordered.lastIndex)]
            } else null

            for (r in rule.range.top.row..rule.range.bottom.row) {
                for (c in rule.range.top.col..rule.range.bottom.col) {
                    val a1 = SheetRange.Cell(c, r).toA1()
                    if (a1 in stopped) continue
                    val cv = values[a1] ?: FormulaEval.CellValue.Empty
                    var matched = false
                    when (rule) {
                        is Rule.CellIs -> if (cv is FormulaEval.CellValue.Num) {
                            val x = cv.value
                            matched = when (rule.op) {
                                Operator.GT -> x > rule.threshold1
                                Operator.GTE -> x >= rule.threshold1
                                Operator.LT -> x < rule.threshold1
                                Operator.LTE -> x <= rule.threshold1
                                Operator.EQ -> x == rule.threshold1
                                Operator.NEQ -> x != rule.threshold1
                                Operator.BETWEEN -> x >= minOf(rule.threshold1, rule.threshold2) &&
                                    x <= maxOf(rule.threshold1, rule.threshold2)
                            }
                            if (matched) {
                                tagsByCell.getOrPut(a1) { ArrayList() }.add(rule.styleTag)
                                fillByCell[a1] = rule.fill
                            }
                        }
                        is Rule.TextContains -> if (cv != FormulaEval.CellValue.Empty && cv !is FormulaEval.CellValue.Err) {
                            matched = cv.displayText().contains(rule.needle, ignoreCase = true)
                            if (matched) {
                                tagsByCell.getOrPut(a1) { ArrayList() }.add(rule.styleTag)
                                fillByCell[a1] = rule.fill
                            }
                        }
                        is Rule.TopN -> if (cv is FormulaEval.CellValue.Num && topCutoff != null) {
                            matched = if (rule.bottom) cv.value <= topCutoff else cv.value >= topCutoff
                            if (matched) {
                                tagsByCell.getOrPut(a1) { ArrayList() }.add(rule.styleTag)
                                fillByCell[a1] = rule.fill
                            }
                        }
                        is Rule.ColorScale2 -> if (cv is FormulaEval.CellValue.Num && minV != null && maxV != null) {
                            matched = true
                            val t = if (maxV == minV) 0.5 else ((cv.value - minV) / (maxV - minV)).coerceIn(0.0, 1.0)
                            fillByCell[a1] = lerpRgb(rule.minColor, rule.maxColor, t)
                            tagsByCell.getOrPut(a1) { ArrayList() }.add("colorScale")
                        }
                        is Rule.DataBar -> if (cv is FormulaEval.CellValue.Num && minV != null && maxV != null) {
                            matched = true
                            val frac = if (maxV == minV) 1.0 else ((cv.value - minV) / (maxV - minV)).coerceIn(0.0, 1.0)
                            barByCell[a1] = frac
                            if (a1 !in fillByCell) fillByCell[a1] = rule.barColor
                            tagsByCell.getOrPut(a1) { ArrayList() }.add("dataBar")
                        }
                    }
                    if (matched && rule.stopIfTrue) stopped.add(a1)
                }
            }
        }

        val out = LinkedHashMap<String, CellDecoration>()
        val keys = (tagsByCell.keys + fillByCell.keys + barByCell.keys).distinct()
        for (k in keys) {
            out[k] = CellDecoration(
                styleTags = tagsByCell[k]?.toList().orEmpty(),
                fill = fillByCell[k],
                dataBarFraction = barByCell[k],
            )
        }
        return out
    }

    private fun lerpRgb(a: Rgb, b: Rgb, t: Double): Rgb = Rgb(
        r = (a.r + (b.r - a.r) * t).roundToInt().coerceIn(0, 255),
        g = (a.g + (b.g - a.g) * t).roundToInt().coerceIn(0, 255),
        b = (a.b + (b.b - a.b) * t).roundToInt().coerceIn(0, 255),
    )
}
