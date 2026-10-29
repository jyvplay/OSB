// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

import java.util.PriorityQueue

/**
 * Clean-room deterministic workflow DAG scheduler, cycle-witness extractor, and critical-path
 * (`makespanMs`) calculator for `:feature:automation`.
 *
 * Uses Kahn's algorithm with a min-heap (`PriorityQueue<String>`) so any acyclic workflow graph
 * yields a **unique lexicographically-smallest topological execution order**, plus exact earliest
 * start/finish timestamps (`earliestStartMs`, `earliestFinishMs`) and a closed directed cycle
 * witness (`[A, B, C, A]`) whenever a dependency loop is rejected.
 */
object DagTopo {
    const val SCOPE =
        "Lexicographically-deterministic Kahn topological sort, critical-path makespanMs, and directed cycle-witness extraction; runtime coroutine dispatch and retry backoff live in workflow executors."

    const val MAX_TASKS = 4_096

    data class Task(
        val id: String,
        val durationMs: Long = 0L,
        val dependsOn: Set<String> = emptySet(),
    ) {
        init {
            require(id.isNotBlank()) { "task id must not be blank" }
            require(durationMs >= 0L) { "task '$id' negative durationMs $durationMs" }
        }
    }

    data class ScheduledTask(
        val id: String,
        val earliestStartMs: Long,
        val earliestFinishMs: Long,
    )

    sealed interface PlanResult {
        data class Schedule(
            val order: List<ScheduledTask>,
            val makespanMs: Long,
            val criticalPath: List<String>,
        ) : PlanResult

        data class CycleDetected(val cycleWitness: List<String>) : PlanResult
    }

    fun plan(tasks: List<Task>): PlanResult {
        require(tasks.size <= MAX_TASKS) { "workflow task count ${tasks.size} exceeds $MAX_TASKS" }
        if (tasks.isEmpty()) return PlanResult.Schedule(emptyList(), 0L, emptyList())

        val byId = LinkedHashMap<String, Task>(tasks.size)
        for (t in tasks) {
            require(t.id !in byId) { "duplicate task id '${t.id}'" }
            byId[t.id] = t
        }
        for (t in tasks) {
            for (dep in t.dependsOn) {
                require(dep in byId) { "task '${t.id}' depends on unknown task '$dep'" }
            }
        }

        val inDegree = HashMap<String, Int>(tasks.size)
        val dependents = HashMap<String, MutableList<String>>(tasks.size)
        for (t in tasks) {
            inDegree[t.id] = t.dependsOn.size
            for (dep in t.dependsOn) {
                dependents.getOrPut(dep) { ArrayList() }.add(t.id)
            }
        }

        val ready = PriorityQueue<String>()
        for ((id, deg) in inDegree) {
            if (deg == 0) ready.add(id)
        }

        val earliestStart = HashMap<String, Long>(tasks.size)
        val earliestFinish = HashMap<String, Long>(tasks.size)
        val predecessorOnCritical = HashMap<String, String?>()
        val ordered = ArrayList<ScheduledTask>(tasks.size)

        while (ready.isNotEmpty()) {
            val id = ready.poll()
            val task = byId[id]!!
            var maxStart = 0L
            var bestPred: String? = null
            for (dep in task.dependsOn.sorted()) {
                val depFin = earliestFinish[dep]!!
                if (depFin > maxStart || (depFin == maxStart && bestPred == null && task.dependsOn.isNotEmpty())) {
                    maxStart = depFin
                    bestPred = dep
                }
            }
            val fin = Math.addExact(maxStart, task.durationMs)
            earliestStart[id] = maxStart
            earliestFinish[id] = fin
            predecessorOnCritical[id] = bestPred
            ordered.add(ScheduledTask(id, maxStart, fin))

            for (nxt in dependents[id].orEmpty()) {
                val rem = inDegree[nxt]!! - 1
                inDegree[nxt] = rem
                if (rem == 0) ready.add(nxt)
            }
        }

        if (ordered.size < tasks.size) {
            return PlanResult.CycleDetected(extractCycleWitness(byId, inDegree))
        }

        val sink = ordered.maxWith(compareBy<ScheduledTask> { it.earliestFinishMs }.thenByDescending { "" }.thenBy { it.id })
        // Tie-break lexicographically smallest sink among maximum-finish sinks:
        val maxMakespan = ordered.maxOf { it.earliestFinishMs }
        val bestSink = ordered.filter { it.earliestFinishMs == maxMakespan }.minBy { it.id }
        val critPath = ArrayList<String>()
        var cur: String? = bestSink.id
        while (cur != null) {
            critPath.add(cur)
            cur = predecessorOnCritical[cur]
        }
        critPath.reverse()
        check(sink.earliestFinishMs <= maxMakespan)
        return PlanResult.Schedule(ordered, maxMakespan, critPath)
    }

    private fun extractCycleWitness(byId: Map<String, Task>, remainingInDegree: Map<String, Int>): List<String> {
        val stuck = remainingInDegree.filterValues { it > 0 }.keys.sorted()
        val state = HashMap<String, Int>()
        val stack = ArrayList<String>()

        fun dfs(u: String): List<String>? {
            state[u] = 1
            stack.add(u)
            // Follow dependency edges u -> dep (where dep must run before u); reverse witness at end so arrows read dep -> u
            for (dep in byId[u]!!.dependsOn.sorted()) {
                if ((remainingInDegree[dep] ?: 0) <= 0) continue
                when (state[dep] ?: 0) {
                    1 -> {
                        val idx = stack.indexOf(dep)
                        val loop = stack.subList(idx, stack.size) + dep
                        return loop.reversed()
                    }
                    0 -> dfs(dep)?.let { return it }
                }
            }
            stack.removeAt(stack.size - 1)
            state[u] = 2
            return null
        }

        for (s in stuck) {
            if ((state[s] ?: 0) == 0) {
                dfs(s)?.let { return it }
            }
        }
        error("stuck subgraph must contain at least one directed cycle")
    }
}
