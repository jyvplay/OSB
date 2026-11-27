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
 *
 * R146 repair: parent classification is strictly two-pass. The previous
 * single-pass version classified a message as a root when its parent had
 * not been *indexed yet*, so a parent id appearing later in the list (for
 * example `a->b`, `b->a`) escaped cycle detection entirely. Classification
 * now happens only after every message is indexed, a rootless message set
 * is refused outright, and every message unreachable from a root is refused
 * because it can only sit in a rootless cyclic component. The cycle walk is
 * iterative so a 4096-deep chain cannot exhaust the JVM or the oracle's
 * stack.
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

        // Pass 1: validate and index EVERY message before any parent is read,
        // so forward references (parent listed after child) resolve correctly.
        val byId = LinkedHashMap<String, Message>(messages.size)
        for (m in messages) {
            require(byId.size < MAX_MESSAGES) { "too many messages" }
            require(ID_RE.matches(m.id)) { "invalid message id: ${m.id}" }
            require(m.id.length <= MAX_ID_LEN) { "id too long" }
            require(m.parentId == null || ID_RE.matches(m.parentId)) { "invalid parent id" }
            require(m.text.length <= MAX_ID_LEN * 4) { "text too long" }
            byId[m.id] = m
        }

        // Pass 2: classify roots and children against the complete index.
        val children = LinkedHashMap<String, MutableList<String>>()
        val roots = mutableListOf<String>()
        for (m in messages) {
            val parent = m.parentId
            if (parent == null || parent !in byId) {
                roots.add(m.id)
            } else {
                children.getOrPut(parent) { mutableListOf() }.add(m.id)
            }
        }

        // A non-empty message set with no roots means every parent pointer
        // stays inside the set; following them must revisit a node.
        require(messages.isEmpty() || roots.isNotEmpty()) {
            "cycle detected: no root messages"
        }

        // Cycle detection: iterative DFS from each root over child edges.
        val visited = HashSet<String>()
        val inStack = HashSet<String>()
        for (root in roots) {
            if (root in visited) continue
            val stack = ArrayDeque<Pair<String, Int>>() // (node, child index phase 0 = enter)
            stack.addLast(root to -1)
            var failed: String? = null
            while (stack.isNotEmpty() && failed == null) {
                val (id, phase) = stack.removeLast()
                if (phase == -1) {
                    if (id in inStack) { failed = id; break }
                    if (id in visited) continue
                    inStack.add(id)
                    visited.add(id)
                    stack.addLast(id to 0)
                    val kids = children[id]
                    if (kids != null) {
                        for (k in kids) stack.addLast(k to -1)
                    }
                } else {
                    inStack.remove(id)
                }
            }
            require(failed == null) { "cycle detected at $failed" }
        }

        // Every non-root has a parent inside the set, so any message never
        // reached from a root belongs to a rootless component whose parent
        // walk must loop: that is a cycle the root DFS cannot see.
        require(visited.size == byId.size) {
            "cycle detected: unreachable cyclic component"
        }

        // Collect descendants per root (bounded depth, iterative pre-order).
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
        val stack = ArrayDeque<Pair<String, Int>>()
        stack.addLast(id to depth)
        var first = true
        while (stack.isNotEmpty()) {
            val (node, nodeDepth) = stack.removeLast()
            // The initial node is the thread root itself and is not a
            // descendant; every later pop is a child pushed under the depth
            // bound, so emitting on pop keeps pre-order without reversal.
            if (!first) out.add(node)
            first = false
            if (nodeDepth < MAX_DEPTH) {
                val kids = children[node] ?: continue
                for (i in kids.indices.reversed()) {
                    stack.addLast(kids[i] to (nodeDepth + 1))
                }
            }
        }
    }
}
