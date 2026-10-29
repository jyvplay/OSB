// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Offline Screenshot/Text **PII & Secret Redaction Scanner** (`CREDIT_CARD` Luhn, `US_SSN`,
 * `JWT_BEARER`, `PEM_PRIVATE_KEY`, `EMAIL`) + Overlapping Bounding-Box Merger.
 */
object PiiRedactionScanner {
    const val SCOPE =
        "Offline PII & secret span scanner (Luhn credit card, SSN, JWT, PEM private key, email) and overlapping redaction rectangle merger."

    enum class Kind { CREDIT_CARD, US_SSN, JWT_TOKEN, PEM_PRIVATE_KEY, EMAIL }

    data class SpanHit(val kind: Kind, val start: Int, val endExclusive: Int)
    data class BoxPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun intersectsOrTouches(other: BoxPx): Boolean =
            left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

        fun union(other: BoxPx): BoxPx = BoxPx(
            minOf(left, other.left),
            minOf(top, other.top),
            maxOf(right, other.right),
            maxOf(bottom, other.bottom),
        )
    }

    fun scanText(text: String): List<SpanHit> {
        val hits = ArrayList<SpanHit>()
        for (m in Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----").findAll(text)) {
            hits.add(SpanHit(Kind.PEM_PRIVATE_KEY, m.range.first, m.range.last + 1))
        }
        for (m in Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b").findAll(text)) {
            hits.add(SpanHit(Kind.JWT_TOKEN, m.range.first, m.range.last + 1))
        }
        for (m in Regex("\\b(?!000|666|9\\d\\d)\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b").findAll(text)) {
            hits.add(SpanHit(Kind.US_SSN, m.range.first, m.range.last + 1))
        }
        for (m in Regex("\\b(?:\\d[ -]*?){13,19}\\b").findAll(text)) {
            val digits = m.value.filter { it.isDigit() }
            if (digits.length in 13..19 && passesLuhn(digits)) {
                hits.add(SpanHit(Kind.CREDIT_CARD, m.range.first, m.range.last + 1))
            }
        }
        for (m in Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b").findAll(text)) {
            hits.add(SpanHit(Kind.EMAIL, m.range.first, m.range.last + 1))
        }
        return hits.sortedBy { it.start }
    }

    fun redactText(text: String): String {
        val hits = scanText(text)
        if (hits.isEmpty()) return text
        val sb = StringBuilder(text)
        // Merge overlapping character spans before replacing right-to-left.
        val merged = ArrayList<Pair<Int, Pair<Int, Kind>>>()
        for (h in hits) {
            if (merged.isEmpty() || h.start > merged.last().second.first) {
                merged.add(h.start to (h.endExclusive to h.kind))
            } else {
                val last = merged.removeAt(merged.lastIndex)
                merged.add(last.first to (maxOf(last.second.first, h.endExclusive) to last.second.second))
            }
        }
        for ((st, endKind) in merged.asReversed()) {
            sb.replace(st, endKind.first, "[REDACTED:${endKind.second.name}]")
        }
        return sb.toString()
    }

    fun mergeRedactionBoxes(boxes: List<BoxPx>): List<BoxPx> {
        val remaining = boxes.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            outer@ for (i in remaining.indices) {
                for (j in i + 1 until remaining.size) {
                    if (remaining[i].intersectsOrTouches(remaining[j])) {
                        val u = remaining[i].union(remaining[j])
                        remaining.removeAt(j)
                        remaining[i] = u
                        changed = true
                        break@outer
                    }
                }
            }
        }
        return remaining.sortedWith(compareBy<BoxPx> { it.top }.thenBy { it.left })
    }

    private fun passesLuhn(digits: String): Boolean {
        var sum = 0
        var dbl = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (dbl) { d *= 2; if (d > 9) d -= 9 }
            sum += d
            dbl = !dbl
        }
        return sum % 10 == 0
    }
}
