// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Bounded command history ring with consecutive-duplicate suppression and
 * prefix search.
 */
class CommandHistoryRing private constructor(private val capacity: Int) {
    companion object {
        const val MAX_CAPACITY = 2048
        const val MAX_CMD_LEN = 1024

        fun create(capacity: Int): CommandHistoryRing {
            require(capacity in 1..MAX_CAPACITY) { "capacity out of bounds" }
            return CommandHistoryRing(capacity)
        }
    }

    private val ring = ArrayDeque<String>(capacity)

    fun record(cmd: String) {
        require(cmd.isNotEmpty() && cmd.length <= MAX_CMD_LEN) { "cmd length out of bounds" }
        if (ring.isNotEmpty() && ring.last() == cmd) return
        if (ring.size == capacity) ring.removeFirst()
        ring.addLast(cmd)
    }

    fun searchPrefix(prefix: String): List<String> {
        require(prefix.length <= MAX_CMD_LEN) { "prefix too long" }
        return ring.filter { it.startsWith(prefix) }.reversed()
    }

    fun size(): Int = ring.size
    fun clear() = ring.clear()
}
