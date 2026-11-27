// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Bounded text selection range. Represents a text selection with start and
 * end offsets (inclusive start, exclusive end) within a document of bounded
 * length. Supports selection normalization (start <= end), clamping to
 * document bounds, collapse (start == end), and extension. Does not
 * manipulate any real text view — it is a pure range model.
 *
 * Declared narrowings: document length at most [MAX_DOC_LEN] chars;
 * offsets non-negative and at most [MAX_DOC_LEN]; selection length at most
 * [MAX_DOC_LEN]; collapsed selections have equal start and end.
 */
class TextSelectionRange private constructor(
    private val docLen: Int,
    private var start: Int,
    private var end: Int
) {
    companion object {
        const val MAX_DOC_LEN = 1 shl 20 // 1 MiB chars

        fun create(docLen: Int, start: Int, end: Int): TextSelectionRange {
            require(docLen >= 0 && docLen <= MAX_DOC_LEN) { "docLen out of bounds" }
            require(start >= 0 && start <= docLen) { "start out of bounds" }
            require(end >= 0 && end <= docLen) { "end out of bounds" }
            return TextSelectionRange(docLen, start, end).normalize()
        }

        fun collapsed(docLen: Int, offset: Int): TextSelectionRange {
            require(docLen >= 0 && docLen <= MAX_DOC_LEN) { "docLen out of bounds" }
            require(offset >= 0 && offset <= docLen) { "offset out of bounds" }
            return TextSelectionRange(docLen, offset, offset)
        }
    }

    private fun normalize(): TextSelectionRange {
        var s = start
        var e = end
        if (s > e) {
            val tmp = s; s = e; e = tmp
        }
        s = s.coerceIn(0, docLen)
        e = e.coerceIn(0, docLen)
        return TextSelectionRange(docLen, s, e)
    }

    fun start(): Int = start
    fun end(): Int = end
    fun length(): Int = end - start
    fun isCollapsed(): Boolean = start == end
    fun isEmpty(): Boolean = end == start

    fun clamp(newStart: Int, newEnd: Int): TextSelectionRange {
        require(newStart >= 0 && newStart <= docLen) { "newStart out of bounds" }
        require(newEnd >= 0 && newEnd <= docLen) { "newEnd out of bounds" }
        return TextSelectionRange(docLen, newStart, newEnd).normalize()
    }

    fun collapseToStart(): TextSelectionRange = TextSelectionRange(docLen, start, start)

    fun collapseToEnd(): TextSelectionRange = TextSelectionRange(docLen, end, end)

    fun extendBy(deltaStart: Int, deltaEnd: Int): TextSelectionRange {
        val newStart = (start + deltaStart).coerceIn(0, docLen)
        val newEnd = (end + deltaEnd).coerceIn(0, docLen)
        return TextSelectionRange(docLen, newStart, newEnd).normalize()
    }

    fun contains(offset: Int): Boolean = offset >= start && offset < end

    fun documentLength(): Int = docLen

    private fun Int.coerceIn(min: Int, max: Int): Int =
        if (this < min) min else if (this > max) max else this
}
