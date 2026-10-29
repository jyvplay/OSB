// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * Clean-room Kahn topological DAG workflow planner & critical-path calculator (`DagTaskRunner`).
 */
object DagTaskRunner {
    const val SCOPE =
        "Topological DAG workflow wave planner + critical-path duration calculator + deterministic cycle refusal; distributed worker RPC out of scope."

    data class TaskSpec(
        val id: String,
        val durationMs: Long,
        val dependsOn: Set<String> = emptySet(),
    ) {
        init {
            require(id.isNotBlank() && durationMs >= 0L) { "Invalid TaskSpec: $id" }
        }
    }

    data class Plan(
        val parallelWaves: List<List<String>>,
        val criticalPathMs: Long,
    )

    fun buildPlan(tasks: List<TaskSpec>): Plan {
        if (tasks.isEmpty()) return Plan(emptyList(), 0L)
        val byId = tasks.associateBy { it.id }
        require(byId.size == tasks.size) { "Duplicate task IDs in DAG" }

        val inDegree = LinkedHashMap<String, Int>()
        val dependents = LinkedHashMap<String, MutableList<String>>()
        for (t in tasks) {
            for (dep in t.dependsOn) {
                require(byId.containsKey(dep)) { "Task '${t.id}' depends on unknown task '$dep'" }
                dependents.getOrPut(dep) { mutableListOf() }.add(t.id)
            }
            inDegree[t.id] = t.dependsOn.size
        }

        val earliestFinish = HashMap<String, Long>()
        val waves = mutableListOf<List<String>>()
        var remaining = tasks.size

        while (remaining > 0) {
            val ready = inDegree.entries.filter { it.value == 0 }.map { it.key }.sorted()
            require(ready.isNotEmpty()) { "Cycle detected in automation workflow DAG" }
            waves.add(ready)
            for (id in ready) {
                inDegree.remove(id)
                remaining--
                val spec = byId.getValue(id)
                val maxPred = spec.dependsOn.maxOfOrNull { earliestFinish.getValue(it) } ?: 0L
                earliestFinish[id] = maxPred + spec.durationMs
                for (child in dependents[id].orEmpty()) {
                    inDegree[child] = inDegree.getValue(child) - 1
                }
            }
        }
        return Plan(waves, earliestFinish.values.maxOrNull() ?: 0L)
    }
}
