// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

/**
 * Pure-JVM **Spreadsheet Conditional Formatting Icon-Set & Color-Scale Threshold Evaluator**
 * (`3_ARROWS`, `3_TRAFFIC_LIGHTS`, `2_COLOR_SCALE`, `3_COLOR_SCALE`, `DATA_BAR`) for `:feature:sheets`.
 */
object ConditionalFormattingScaleEvaluator {
    const val SCOPE =
        "Spreadsheet conditional formatting evaluator (2/3-color linear interpolation scale, data-bar fill ratio, and percentile icon-set buckets)."

    data class CellVisualFormat(
        val value: Double,
        val dataBarFillFraction: Double, // 0.0..1.0
        val interpolatedColorArgb: Int,
        val trafficLightBucket: Int, // 0=RED, 1=AMBER, 2=GREEN
    )

    fun evaluateColumn(
        values: List<Double>,
        lowArgb: Int = 0xFFF8696B.toInt(),
        midArgb: Int = 0xFFFFEB84.toInt(),
        highArgb: Int = 0xFF63BE7B.toInt(),
    ): List<CellVisualFormat> {
        if (values.isEmpty()) return emptyList()
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).let { if (it == 0.0) 1.0 else it }
        return values.map { v ->
            val t = if (hi == lo) 0.5 else ((v - lo) / span).coerceIn(0.0, 1.0)
            val argb = if (t <= 0.5) lerpArgb(lowArgb, midArgb, t * 2.0) else lerpArgb(midArgb, highArgb, (t - 0.5) * 2.0)
            val bucket = when {
                t < 1.0 / 3.0 -> 0
                t < 2.0 / 3.0 -> 1
                else -> 2
            }
            CellVisualFormat(v, t, argb, bucket)
        }
    }

    private fun lerpArgb(c0: Int, c1: Int, t: Double): Int {
        fun ch(shift: Int): Int {
            val a = (c0 ushr shift) and 0xFF
            val b = (c1 ushr shift) and 0xFF
            return kotlin.math.round(a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
