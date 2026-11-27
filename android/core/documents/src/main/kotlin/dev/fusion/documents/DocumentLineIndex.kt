// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Bounded document line-start offset index. Builds an array of 0-based line
 * start offsets and provides O(log N) binary-search conversion between
 * character offsets and (line, column) pairs.
 */
class DocumentLineIndex private constructor(
    private val lineStarts: IntArray,
    private val docLen: Int
) {
    companion object {
        const val MAX_DOC_LEN = 1 shl 22 // 4 MiB chars

        fun build(text: String): DocumentLineIndex {
            require(text.length <= MAX_DOC_LEN) { "document exceeds MAX_DOC_LEN" }
            val starts = mutableListOf(0)
            for (i in text.indices) {
                if (text[i] == '\n') {
                    starts.add(i + 1)
                }
            }
            return DocumentLineIndex(starts.toIntArray(), text.length)
        }
    }

    fun lineCount(): Int = lineStarts.size

    fun offsetToLineCol(offset: Int): Pair<Int, Int> {
        require(offset in 0..docLen) { "offset out of bounds: $offset" }
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lineStarts[mid] <= offset) {
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val line = hi.coerceAtLeast(0)
        return line to (offset - lineStarts[line])
    }

    fun lineColToOffset(line: Int, col: Int): Int {
        require(line in 0 until lineStarts.size) { "line out of bounds: $line" }
        require(col >= 0) { "negative col: $col" }
        val lineStart = lineStarts[line]
        val lineEnd = if (line + 1 < lineStarts.size) lineStarts[line + 1] - 1 else docLen
        val clamped = (lineStart + col).coerceAtMost(lineEnd)
        return clamped
    }
}
