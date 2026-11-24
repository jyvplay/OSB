// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Bounded circular scrollback ring buffer for terminal lines.
 * Enforces [capacity] <= [MAX_CAPACITY] and per-line length <= [MAX_LINE_LEN].
 */
class TerminalScrollbackRing private constructor(
    private val capacity: Int
) {
    companion object {
        const val MAX_CAPACITY = 10_000
        const val MAX_LINE_LEN = 4096

        fun create(capacity: Int): TerminalScrollbackRing {
            require(capacity in 1..MAX_CAPACITY) { "capacity out of bounds: $capacity" }
            return TerminalScrollbackRing(capacity)
        }
    }

    private val buffer = ArrayDeque<String>(capacity)

    fun push(line: String) {
        require(line.length <= MAX_LINE_LEN) { "line too long: ${line.length}" }
        if (buffer.size == capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(line)
    }

    fun size(): Int = buffer.size
    fun capacity(): Int = capacity

    fun lines(): List<String> = buffer.toList()

    fun tail(n: Int): List<String> {
        require(n >= 0) { "negative tail count" }
        if (n == 0 || buffer.isEmpty()) return emptyList()
        val count = if (n > buffer.size) buffer.size else n
        return buffer.toList().subList(buffer.size - count, buffer.size)
    }

    fun clear() = buffer.clear()
}
