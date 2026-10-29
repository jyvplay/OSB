// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.util.Locale

/**
 * Clean-room ECMA-376 Office 2010+ (`x14:sparklineGroups`) in-cell sparkline XML serializer
 * and normalized vector plot geometry generator (`LINE`, `COLUMN`, `STACKED_WIN_LOSS`).
 */
object SheetSparklineRenderer {
    enum class SparkKind(val ooxmlType: String) {
        LINE("line"),
        COLUMN("column"),
        WIN_LOSS("stacked"),
    }

    data class SparklineDef(
        val targetCellA1: String,
        val sourceRangeA1: String,
        val kind: SparkKind = SparkKind.LINE,
        val colorHex: String = "38BDF8",
        val highlightHighLow: Boolean = true,
    ) {
        init {
            SheetRange.parseCell(targetCellA1.replace("$", "").uppercase())
            SheetRange.parse(sourceRangeA1.replace("$", "").uppercase())
            require(colorHex.matches(Regex("[0-9A-Fa-f]{6}"))) { "sparkline color must be 6 hex digits" }
        }
    }

    data class SparkBar(val index: Int, val normalizedTop: Double, val normalizedHeight: Double, val isHigh: Boolean, val isLow: Boolean)

    data class SparkGeometry(
        val kind: SparkKind,
        val pointsNormalized: List<Pair<Double, Double>>,
        val bars: List<SparkBar>,
        val highIndex: Int,
        val lowIndex: Int,
    )

    fun buildWorksheetExtXml(sheetName: String, defs: List<SparklineDef>): String {
        require(sheetName.isNotBlank() && defs.isNotEmpty()) { "sheetName and defs required" }
        val byKind = defs.groupBy { it.kind to it.colorHex }
        return buildString {
            append("""<ext uri="{05C60535-1F16-4fd2-B633-F4F36F0B64E0}" xmlns:x14="http://schemas.microsoft.com/office/spreadsheetml/2009/9/main" xmlns:xm="http://schemas.microsoft.com/office/excel/2006/main">""")
            append("<x14:sparklineGroups>")
            for ((keyPair, group) in byKind) {
                val (kind, clr) = keyPair
                val hl = if (group.any { it.highlightHighLow }) """ high="1" low="1"""" else ""
                append("""<x14:sparklineGroup type="${kind.ooxmlType}"$hl>""")
                append("""<x14:colorSeries rgb="FF${clr.uppercase(Locale.ROOT)}"/>""")
                append("<x14:sparklines>")
                for (d in group) {
                    append("<x14:sparkline>")
                    append("<xm:f>${escapeXml(sheetName)}!${d.sourceRangeA1.uppercase(Locale.ROOT)}</xm:f>")
                    append("<xm:sqref>${d.targetCellA1.uppercase(Locale.ROOT)}</xm:sqref>")
                    append("</x14:sparkline>")
                }
                append("</x14:sparklines></x14:sparklineGroup>")
            }
            append("</x14:sparklineGroups></ext>")
        }
    }

    fun computeGeometry(values: List<Double>, kind: SparkKind = SparkKind.LINE): SparkGeometry {
        require(values.isNotEmpty() && values.all { it.isFinite() }) { "sparkline values must be finite & non-empty" }
        val hiIdx = values.indices.maxByOrNull { values[it] } ?: 0
        val loIdx = values.indices.minByOrNull { values[it] } ?: 0
        val minV = values[loIdx]
        val maxV = values[hiIdx]
        val span = (maxV - minV).let { if (it == 0.0) 1.0 else it }

        val pts = values.mapIndexed { i, v ->
            val x = if (values.size == 1) 0.5 else i.toDouble() / (values.size - 1)
            val y = if (maxV == minV) 0.5 else 1.0 - (v - minV) / span
            x to y.coerceIn(0.0, 1.0)
        }

        val bars = values.mapIndexed { i, v ->
            when (kind) {
                SparkKind.WIN_LOSS -> {
                    when {
                        v > 0.0 -> SparkBar(i, 0.05, 0.45, i == hiIdx, i == loIdx)
                        v < 0.0 -> SparkBar(i, 0.50, 0.45, i == hiIdx, i == loIdx)
                        else -> SparkBar(i, 0.49, 0.02, i == hiIdx, i == loIdx)
                    }
                }
                else -> {
                    val zeroNorm = when {
                        minV >= 0.0 -> 1.0
                        maxV <= 0.0 -> 0.0
                        else -> 1.0 - (0.0 - minV) / span
                    }
                    val vNorm = if (maxV == minV) 0.5 else 1.0 - (v - minV) / span
                    val top = minOf(zeroNorm, vNorm)
                    val h = kotlin.math.abs(zeroNorm - vNorm).coerceAtLeast(0.04)
                    SparkBar(i, top, h, i == hiIdx, i == loIdx)
                }
            }
        }
        return SparkGeometry(kind, pts, bars, hiIdx, loIdx)
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
