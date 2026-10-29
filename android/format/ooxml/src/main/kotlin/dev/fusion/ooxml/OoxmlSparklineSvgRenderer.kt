// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.util.Locale

/**
 * Clean-room OOXML `<x14:sparklineGroups>` XML Serializer & Crisp Inline Line / Column / Win-Loss
 * SVG Sparkline Renderer with High / Low / Negative Point Markers.
 */
object OoxmlSparklineSvgRenderer {
    const val SCOPE =
        "Pure-JVM Spreadsheet <x14:sparklineGroups> XML serializer and Line/Column/WinLoss inline SVG sparkline renderer."

    enum class SparklineKind(val ooxmlAttr: String) { LINE("stacked"), COLUMN("column"), WIN_LOSS("stacked") }

    fun buildSparklineGroupXml(kind: SparklineKind, dataFormulaRange: String, hostCellRef: String): String {
        require('!' in dataFormulaRange && hostCellRef.matches(Regex("^[A-Z]+[1-9]\\d*$"))) {
            "expected Sheet!Range formula and A1 host cell"
        }
        return buildString {
            append("""<x14:sparklineGroups xmlns:x14="http://schemas.microsoft.com/office/spreadsheetml/2009/9/main" """)
            append("""xmlns:xm="http://schemas.microsoft.com/office/excel/2006/main">""")
            val typeAttr = if (kind == SparklineKind.LINE) "" else """ type="${kind.ooxmlAttr}""""
            val wlAttr = if (kind == SparklineKind.WIN_LOSS) """ dateAxis="0" manualMax="1" manualMin="-1"""" else ""
            append("""<x14:sparklineGroup$typeAttr$wlAttr high="1" low="1" negative="1">""")
            append("<x14:sparklines><x14:sparkline>")
            append("<xm:f>$dataFormulaRange</xm:f><xm:sqref>$hostCellRef</xm:sqref>")
            append("</x14:sparkline></x14:sparklines></x14:sparklineGroup></x14:sparklineGroups>")
        }
    }

    fun renderInlineSvg(kind: SparklineKind, series: DoubleArray, widthPx: Int = 120, heightPx: Int = 28): String {
        require(series.size >= 2 && series.all { it.isFinite() } && widthPx >= 24 && heightPx >= 12) {
            "sparkline requires >=2 finite values"
        }
        val mn = series.min()
        val mx = series.max()
        val span = (mx - mn).takeIf { it > 1e-12 } ?: 1.0
        val pad = 3.0
        val usableW = widthPx - 2 * pad
        val usableH = heightPx - 2 * pad
        fun mapY(v: Double): Double = heightPx - pad - ((v - mn) / span) * usableH

        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $widthPx $heightPx">""")
            when (kind) {
                SparklineKind.LINE -> {
                    val pts = series.indices.joinToString(" ") { i ->
                        val x = pad + (i.toDouble() / (series.size - 1)) * usableW
                        String.format(Locale.US, "%.2f,%.2f", x, mapY(series[i]))
                    }
                    append("""<polyline fill="none" stroke="#38BDF8" stroke-width="1.6" points="$pts"/>""")
                    val hiIdx = series.indices.maxBy { series[it] }
                    val loIdx = series.indices.minBy { series[it] }
                    val hx = pad + (hiIdx.toDouble() / (series.size - 1)) * usableW
                    val lx = pad + (loIdx.toDouble() / (series.size - 1)) * usableW
                    append(String.format(Locale.US, """<circle cx="%.2f" cy="%.2f" r="2.2" fill="#22C55E"/>""", hx, mapY(series[hiIdx])))
                    append(String.format(Locale.US, """<circle cx="%.2f" cy="%.2f" r="2.2" fill="#F97316"/>""", lx, mapY(series[loIdx])))
                }
                SparklineKind.COLUMN, SparklineKind.WIN_LOSS -> {
                    val slotW = usableW / series.size
                    val barW = (slotW * 0.72).coerceAtLeast(1.5)
                    val zeroY = if (kind == SparklineKind.WIN_LOSS) heightPx / 2.0 else mapY(0.0.coerceIn(mn, mx))
                    for (i in series.indices) {
                        val v = if (kind == SparklineKind.WIN_LOSS) kotlin.math.sign(series[i]) else series[i]
                        val x = pad + i * slotW
                        val vy = if (kind == SparklineKind.WIN_LOSS) (heightPx / 2.0 - v * (usableH * 0.42)) else mapY(v)
                        val top = minOf(zeroY, vy)
                        val bh = kotlin.math.abs(vy - zeroY).coerceAtLeast(1.0)
                        val color = if (series[i] < 0.0) "#EF4444" else "#38BDF8"
                        append(String.format(Locale.US, """<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" fill="%s"/>""", x, top, barW, bh, color))
                    }
                }
            }
            append("</svg>")
        }
    }
}
