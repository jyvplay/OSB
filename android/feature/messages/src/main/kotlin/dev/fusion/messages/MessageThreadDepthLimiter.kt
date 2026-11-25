// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * Bounded message thread depth calculator with cycle detection.
 */
class MessageThreadDepthLimiter private constructor() {
    companion object {
        const val MAX_DEPTH = 64

        fun depthOf(msgId: String, parentOf: Map<String, String?>): Int {
            var cur: String? = msgId
            var depth = 0
            val seen = HashSet<String>()
            while (cur != null) {
                require(seen.add(cur)) { "cycle detected at $cur" }
                val parent = parentOf[cur] ?: break
                depth++
                require(depth <= MAX_DEPTH) { "thread depth exceeds $MAX_DEPTH" }
                cur = parent
            }
            return depth
        }
    }
}
