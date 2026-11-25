// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * Production-grade RFC-5322 message threading & cycle-safe depth-annotated tree builder.
 */
class MessagesThreadSorter private constructor() {
    companion object {
        data class RawMessage(
            val id: String,
            val inReplyTo: String?,
            val timestampMs: Long
        )

        data class ThreadedNode(
            val id: String,
            val depth: Int,
            val timestampMs: Long
        )

        fun buildThreadOrder(messages: List<RawMessage>): List<ThreadedNode> {
            val byId = messages.associateBy { it.id }
            val children = LinkedHashMap<String?, MutableList<RawMessage>>()
            for (m in messages.sortedBy { it.timestampMs }) {
                val parent = if (m.inReplyTo != null && m.inReplyTo in byId && m.inReplyTo != m.id) {
                    m.inReplyTo
                } else null
                children.getOrPut(parent) { mutableListOf() }.add(m)
            }
            val result = mutableListOf<ThreadedNode>()
            val visited = HashSet<String>()

            fun dfs(parentId: String?, depth: Int) {
                for (child in children[parentId].orEmpty()) {
                    if (visited.add(child.id)) {
                        result.add(ThreadedNode(child.id, depth, child.timestampMs))
                        dfs(child.id, depth + 1)
                    }
                }
            }
            dfs(null, 0)
            return result
        }
    }
}
