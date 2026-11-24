// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * Bounded message thread resolver. Groups messages into threads by
 * parent-message id: each message has an id and an optional parent id.
 * Messages without a parent are thread roots. Returns the roots and a map
 * from root id to its descendant message ids (depth-first, bounded depth).
 *
 * Declared narrowings: at most [MAX_MESSAGES] messages; message ids at most
 * [MAX_ID_LEN] chars; parent ids must reference an existing message id;
 * cycle detection refuses messages that would create a cycle; max depth
 * [MAX_DEPTH] for descendant traversal.
 */
class MessageThread private constructor() {
    companion object {
        const val MAX_MESSAGES = 4096
        const val MAX_ID_LEN = 256
        const val MAX_DEPTH = 32
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")

        data class Message(
            val id: String,
            val parentId: String?,
            val text: String
        )

        data class Thread(
            val rootId: String,
            val descendantIds: List<String>
        )

        fun empty(): MessageThread = MessageThread()
    }

    fun addMessage(id: String, parentId: String?, text: String) {
        require(ID_RE.matches(id)) { "invalid message id: $id" }
        require(id.length <= MAX_ID_LEN) { "id too long" }
        require(parentId == null || ID_RE.matches(parentId!!)) { "invalid parent id" }
        require(text.length <= MAX_ID_LEN * 4) { "text too long" }
    }

    fun resolve(messages: List<Message>): List<Thread> {
        require(messages.size <= MAX_MESSAGES) { "too many messages: $MAX_MESSAGES" }
        val byId = mutableMapOf<String, Message>()
        val children = mutableMapOf<String, MutableList<String>>()
        val roots = mutableListOf<String>()

        for (m in messages) {
            require(byId.size < MAX_MESSAGES) { "too many messages" }
            byId[m.id] = m
            if (m.parentId == null || !byId.containsKey(m.parentId)) {
                roots.add(m.id)
            } else {
                children.getOrPut(m.parentId!!) { mutableListOf() }.add(m.id)
            }
        }

        // Cycle detection: DFS from each root
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()
        fun dfs(id: String): Boolean {
            if (id in inStack) return true  // cycle
            if (id in visited) return false
            inStack.add(id)
            visited.add(id)
            for (child in children.getOrEmpty(id)) {
                if (dfs(child)) return true
            }
            inStack.remove(id)
            return false
        }
        for (root in roots) {
            require(!dfs(root)) { "cycle detected at $root" }
        }

        // Collect descendants per root (bounded depth)
        val threads = mutableListOf<Thread>()
        for (root in roots) {
            val desc = mutableListOf<String>()
            collectDescendants(root, children, 0, desc)
            threads.add(Thread(root, desc))
        }
        return threads.toList()
    }

    private fun collectDescendants(
        id: String, children: Map<String, MutableList<String>>,
        depth: Int, out: MutableList<String>
    ) {
        if (depth >= MAX_DEPTH) return
        for (child in children.getOrEmpty(id)) {
            out.add(child)
            collectDescendants(child, children, depth + 1, out)
        }
    }
}
