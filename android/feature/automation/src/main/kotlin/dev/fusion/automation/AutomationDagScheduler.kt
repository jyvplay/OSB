// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * Production-grade DAG workflow & Kahn's topological execution scheduler.
 */
class AutomationDagScheduler private constructor() {
    companion object {
        fun topologicalOrder(nodes: List<String>, edges: List<Pair<String, String>>): List<String> {
            val inDegree = LinkedHashMap<String, Int>()
            val adj = LinkedHashMap<String, MutableList<String>>()
            for (n in nodes) {
                inDegree[n] = 0
                adj[n] = mutableListOf()
            }
            for ((u, v) in edges) {
                require(u in inDegree && v in inDegree) { "unknown node in edge: $u -> $v" }
                adj[u]!!.add(v)
                inDegree[v] = inDegree[v]!! + 1
            }
            val queue = ArrayDeque<String>()
            for ((n, deg) in inDegree) {
                if (deg == 0) queue.addLast(n)
            }
            val order = mutableListOf<String>()
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                order.add(cur)
                for (nxt in adj[cur].orEmpty()) {
                    val nd = inDegree[nxt]!! - 1
                    inDegree[nxt] = nd
                    if (nd == 0) queue.addLast(nxt)
                }
            }
            require(order.size == nodes.size) { "cycle detected in automation DAG" }
            return order
        }
    }
}
