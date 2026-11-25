// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Bounded greedy word-boundary line wrapper.
 */
class EditorLineWrapCalculator private constructor() {
    companion object {
        fun wrap(text: String, maxCols: Int): List<String> {
            require(maxCols >= 1) { "maxCols must be >= 1" }
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return emptyList()
            val lines = mutableListOf<String>()
            val cur = StringBuilder()
            for (w in words) {
                if (cur.isEmpty()) {
                    cur.append(w)
                } else if (cur.length + 1 + w.length <= maxCols) {
                    cur.append(' ').append(w)
                } else {
                    lines.add(cur.toString())
                    cur.setLength(0)
                    cur.append(w)
                }
            }
            if (cur.isNotEmpty()) lines.add(cur.toString())
            return lines
        }
    }
}
