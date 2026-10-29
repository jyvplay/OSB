// SPDX-License-Identifier: Apache-2.0
package dev.fusion.pdf

/**
 * Clean-room ISO 32000-1 §12.4.2 **PDF `/PageLabels` Number-Tree Serializer & Label Resolver**.
 *
 * Maps 0-based physical PDF page indices to logical book labels such as Roman front-matter
 * (`i, ii, iii, iv`), Arabic chapters (`1, 2, ...`), or prefixed appendices (`A-1, A-2`).
 */
object PdfPageLabelsAndOutline {
    const val SCOPE =
        "Clean-room ISO 32000-1 §12.4.2 PDF /PageLabels number-tree serializer and logical page-label resolver (DECIMAL, LOWER_ROMAN, UPPER_ROMAN, LOWER_ALPHA, UPPER_ALPHA, prefixOnly)."

    enum class NumberStyle(val pdfToken: String?) {
        DECIMAL("/D"),
        UPPER_ROMAN("/R"),
        LOWER_ROMAN("/r"),
        UPPER_ALPHA("/A"),
        LOWER_ALPHA("/a"),
        NONE(null),
    }

    data class LabelRange(
        val startPageZeroBased: Int,
        val style: NumberStyle,
        val prefix: String = "",
        val firstNumber: Int = 1,
    ) {
        init {
            require(startPageZeroBased >= 0) { "startPageZeroBased must be >= 0" }
            require(firstNumber >= 1) { "firstNumber must be >= 1" }
        }
    }

    fun toPageLabelsDict(ranges: List<LabelRange>): String {
        require(ranges.isNotEmpty()) { "ranges must be non-empty" }
        val sorted = ranges.sortedBy { it.startPageZeroBased }
        require(sorted.first().startPageZeroBased == 0) { "PDF /PageLabels /Nums must start at page index 0" }
        require(sorted.zipWithNext().all { (a, b) -> a.startPageZeroBased < b.startPageZeroBased }) {
            "duplicate startPageZeroBased in /PageLabels"
        }
        val nums = sorted.joinToString(" ") { r ->
            val parts = ArrayList<String>()
            parts.add("/Type /PageLabel")
            if (r.style.pdfToken != null) parts.add("/S ${r.style.pdfToken}")
            if (r.prefix.isNotEmpty()) parts.add("/P (${r.prefix.replace("(", "\\(").replace(")", "\\)")})")
            if (r.firstNumber != 1) parts.add("/St ${r.firstNumber}")
            "${r.startPageZeroBased} << ${parts.joinToString(" ")} >>"
        }
        return "<< /Nums [ $nums ] >>"
    }

    fun resolveLabels(totalPages: Int, ranges: List<LabelRange>): List<String> {
        require(totalPages >= 1) { "totalPages must be >= 1" }
        val sorted = ranges.sortedBy { it.startPageZeroBased }
        require(sorted.isNotEmpty() && sorted.first().startPageZeroBased == 0) {
            "ranges must start at page index 0"
        }
        return List(totalPages) { pageIdx ->
            val active = sorted.last { it.startPageZeroBased <= pageIdx }
            val num = active.firstNumber + (pageIdx - active.startPageZeroBased)
            val formatted = when (active.style) {
                NumberStyle.DECIMAL -> num.toString()
                NumberStyle.UPPER_ROMAN -> toRoman(num)
                NumberStyle.LOWER_ROMAN -> toRoman(num).lowercase()
                NumberStyle.UPPER_ALPHA -> toAlpha(num).uppercase()
                NumberStyle.LOWER_ALPHA -> toAlpha(num).lowercase()
                NumberStyle.NONE -> ""
            }
            active.prefix + formatted
        }
    }

    private fun toRoman(n: Int): String {
        require(n in 1..3999) { "Roman numeral $n out of 1..3999" }
        val vals = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val syms = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var rem = n
        val sb = StringBuilder()
        for (i in vals.indices) {
            while (rem >= vals[i]) {
                sb.append(syms[i])
                rem -= vals[i]
            }
        }
        return sb.toString()
    }

    private fun toAlpha(n: Int): String {
        val ch = ('a' + ((n - 1) % 26))
        val reps = ((n - 1) / 26) + 1
        return ch.toString().repeat(reps)
    }
}
