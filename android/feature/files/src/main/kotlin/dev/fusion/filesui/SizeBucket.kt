// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * IEC binary size scaling (bytes to KiB/MiB/…) with exact boundaries.
 *
 * Sits next to [FileSort] (ordering), [DupGroup] (identity), and
 * [NameClash] (naming): nothing answers "how big is 1536 bytes in
 * human units". [scale] picks the largest unit whose 1024-power does
 * not exceed the byte count (0 bytes stays bytes; the top unit is PiB
 * and saturates — exabyte file lists are refused nowhere but also not
 * promised unit names). [format] renders one decimal digit, dropping
 * ".0" for whole values and refusing rounding policies beyond that.
 * Tier choice is exact integer math; only the mantissa is floating.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object SizeBucket {
    /** A scaled size: mantissa plus its IEC unit. */
    data class Scaled(val value: Double, val unit: String)

    private val UNITS = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")

    /**
     * Scales [bytes] to the largest fitting IEC unit.
     *
     * @throws IllegalArgumentException on a negative count.
     */
    fun scale(bytes: Long): Scaled {
        require(bytes >= 0) { "negative bytes" }
        var tier = 0
        var div = 1L
        while (tier < UNITS.size - 1 && bytes >= div * 1024L) {
            div *= 1024L
            tier++
        }
        return Scaled(bytes.toDouble() / div.toDouble(), UNITS[tier])
    }

    /**
     * Renders [bytes] like "512 B", "1 KiB", "1.5 MiB".
     *
     * @throws IllegalArgumentException on a negative count.
     */
    fun format(bytes: Long): String {
        val s = scale(bytes)
        if (s.unit == "B") return "$bytes B"
        val rounded = kotlin.math.round(s.value * 10.0) / 10.0
        val mant = if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString()
            else rounded.toString()
        return "$mant ${s.unit}"
    }
}
