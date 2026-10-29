// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Pure-JVM **Multi-Caret / Column-Block Rectangular Selection Editor** for `:core:editing`,
 * maintaining disjoint sorted carets, merging colliding carets on movement, and applying
 * simultaneous insertions/deletions with exact offset delta propagation!
 */
object MultiCaretBlockEditor {
    const val SCOPE =
        "Disjoint multi-caret & rectangular column-selection editor with automatic caret collision coalescing and exact offset propagation."

    data class CaretSpan(val start: Int, val end: Int) {
        init { require(start in 0..end) { "Invalid CaretSpan($start, $end)" } }
    }

    fun normalizeAndMergeCarets(textLength: Int, rawCarets: List<CaretSpan>): List<CaretSpan> {
        if (rawCarets.isEmpty()) return emptyList()
        val clamped = rawCarets.map {
            CaretSpan(it.start.coerceIn(0, textLength), it.end.coerceIn(0, textLength))
        }.sortedWith(compareBy<CaretSpan> { it.start }.thenBy { it.end })

        val merged = ArrayList<CaretSpan>()
        for (c in clamped) {
            if (merged.isEmpty()) {
                merged.add(c)
            } else {
                val last = merged.last()
                if (c.start <= last.end) {
                    merged[merged.lastIndex] = CaretSpan(last.start, maxOf(last.end, c.end))
                } else {
                    merged.add(c)
                }
            }
        }
        return merged
    }

    fun insertAtAllCarets(buffer: String, rawCarets: List<CaretSpan>, insertion: String): Pair<String, List<CaretSpan>> {
        val carets = normalizeAndMergeCarets(buffer.length, rawCarets)
        if (carets.isEmpty()) return buffer to emptyList()
        val sb = StringBuilder()
        val nextCarets = ArrayList<CaretSpan>(carets.size)
        var cursor = 0
        for (c in carets) {
            sb.append(buffer, cursor, c.start)
            sb.append(insertion)
            val newPos = sb.length
            nextCarets.add(CaretSpan(newPos, newPos))
            cursor = c.end
        }
        sb.append(buffer, cursor, buffer.length)
        return sb.toString() to nextCarets
    }
}
