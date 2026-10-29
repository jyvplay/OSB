// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import kotlin.math.roundToInt

/**
 * Clean-room ECMA-376 §18.3.1.10 Conditional Formatting DataBar, 2/3-Stop ColorScale, and IconSet
 * evaluation + SpreadsheetML `<conditionalFormatting>` serializer.
 */
object ConditionalDataBarScaleEngine {
    const val SCOPE =
        "Pure-JVM ECMA-376 conditional formatting evaluator & XML writer (DataBar fill ratios, 2/3-stop sRGB ColorScale interpolation, 3-arrow IconSet thresholds)."

    data class CellVisualRule(
        val value: Double,
        val barFillRatio: Double,
        val fillHexRgb: String,
        val iconBucket: Int, // 0=down, 1=flat, 2=up
    )

    fun evaluateSeries(
        sqref: String,
        values: List<Double>,
        lowHex: String = "F8696B",
        midHex: String? = "FFEB84",
        highHex: String = "63BE7B",
        barHex: String = "638EC6",
    ): Pair<List<CellVisualRule>, String> {
        require(sqref.isNotBlank() && values.isNotEmpty() && values.all { it.isFinite() }) {
            "non-empty finite series required"
        }
        val lo = values.min()
        val hi = values.max()
        val span = if (hi > lo) hi - lo else 1.0
        val cLo = parseHexRgb(lowHex)
        val cMid = midHex?.let(::parseHexRgb)
        val cHi = parseHexRgb(highHex)
        parseHexRgb(barHex)

        val evaluated = values.map { v ->
            val t = if (hi > lo) ((v - lo) / span).coerceIn(0.0, 1.0) else 1.0
            val rgb = if (cMid == null) {
                lerpRgb(cLo, cHi, t)
            } else if (t <= 0.5) {
                lerpRgb(cLo, cMid, t * 2.0)
            } else {
                lerpRgb(cMid, cHi, (t - 0.5) * 2.0)
            }
            val bucket = when {
                t < 1.0 / 3.0 -> 0
                t < 2.0 / 3.0 -> 1
                else -> 2
            }
            CellVisualRule(v, t, formatHexRgb(rgb), bucket)
        }

        val xml = buildString {
            append("""<conditionalFormatting sqref="${sqref.trim()}">""")
            append("""<cfRule type="dataBar" priority="1"><dataBar><cfvo type="min"/><cfvo type="max"/><color rgb="FF${barHex.uppercase()}"/></dataBar></cfRule>""")
            append("""<cfRule type="colorScale" priority="2"><colorScale>""")
            append("""<cfvo type="min"/>""")
            if (midHex != null) append("""<cfvo type="percentile" val="50"/>""")
            append("""<cfvo type="max"/>""")
            append("""<color rgb="FF${lowHex.uppercase()}"/>""")
            if (midHex != null) append("""<color rgb="FF${midHex.uppercase()}"/>""")
            append("""<color rgb="FF${highHex.uppercase()}"/>""")
            append("""</colorScale></cfRule></conditionalFormatting>""")
        }
        return evaluated to xml
    }

    private fun parseHexRgb(h: String): IntArray {
        val clean = h.trim().removePrefix("#").uppercase()
        require(clean.length == 6 && clean.all { it in '0'..'9' || it in 'A'..'F' }) { "invalid 6-hex RGB '$h'" }
        return intArrayOf(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16))
    }

    private fun lerpRgb(a: IntArray, b: IntArray, t: Double): IntArray = IntArray(3) { i ->
        (a[i] + (b[i] - a[i]) * t).roundToInt().coerceIn(0, 255)
    }

    private fun formatHexRgb(rgb: IntArray): String =
        "%02X%02X%02X".format(rgb[0], rgb[1], rgb[2])
}
