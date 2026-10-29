// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Deterministic Shortest-Path Settings Schema Migration Planner & Atomic Executor.
 *
 * Mirrors Jetpack Room / DataStore forward-migration semantics in pure Kotlin:
 * - Given registered forward steps `(fromVersion -> toVersion)` (`fromVersion < toVersion`),
 *   computes the shortest minimum-hop migration chain (preferring larger verified multi-version
 *   jumps when tie-breaking on hop count, then lowest intermediate version).
 * - Refuses downgrade requests (`target < current`), missing migration hops, or duplicate
 *   `(from, to)` registrations, and leaves the original state untouched on any step failure.
 */
object SchemaMigrate {
    const val SCOPE =
        "Forward-only shortest-hop settings schema migration planner and atomic map transformer; SQL DDL reflection out of scope."

    const val MAX_VERSION = 10_000

    data class Step(
        val fromVersion: Int,
        val toVersion: Int,
        val description: String,
        val transform: (Map<String, String>) -> Map<String, String>,
    ) {
        init {
            require(fromVersion in 1 until toVersion && toVersion <= MAX_VERSION) {
                "invalid forward migration step $fromVersion -> $toVersion"
            }
            require(description.isNotBlank()) { "migration description must not be blank" }
        }
    }

    data class MigrationResult(
        val finalVersion: Int,
        val appliedSteps: List<Pair<Int, Int>>,
        val settings: Map<String, String>,
    )

    fun plan(steps: List<Step>, currentVersion: Int, targetVersion: Int): List<Step> {
        require(currentVersion in 1..MAX_VERSION && targetVersion in 1..MAX_VERSION) {
            "versions ($currentVersion -> $targetVersion) outside 1..$MAX_VERSION"
        }
        require(targetVersion >= currentVersion) {
            "downgrade migration ($currentVersion -> $targetVersion) refused"
        }
        val byPair = HashMap<Pair<Int, Int>, Step>()
        val outgoing = HashMap<Int, MutableList<Step>>()
        for (s in steps) {
            require(byPair.put(s.fromVersion to s.toVersion, s) == null) {
                "duplicate migration step ${s.fromVersion} -> ${s.toVersion}"
            }
            outgoing.getOrPut(s.fromVersion) { ArrayList() }.add(s)
        }
        for (list in outgoing.values) {
            // Prefer largest jump first so BFS explores direct shortcuts deterministically
            list.sortByDescending { it.toVersion }
        }
        if (currentVersion == targetVersion) return emptyList()

        // BFS for minimum-hop path from currentVersion to targetVersion
        val prevStep = HashMap<Int, Step>()
        val queue = ArrayDeque<Int>()
        queue.addLast(currentVersion)

        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            if (u == targetVersion) break
            for (edge in outgoing[u].orEmpty()) {
                val v = edge.toVersion
                if (v <= targetVersion && v != currentVersion && v !in prevStep) {
                    prevStep[v] = edge
                    queue.addLast(v)
                }
            }
        }
        require(targetVersion in prevStep) {
            "no migration path from v$currentVersion to v$targetVersion"
        }

        val path = ArrayList<Step>()
        var cur = targetVersion
        while (cur != currentVersion) {
            val st = prevStep.getValue(cur)
            path.add(st)
            cur = st.fromVersion
        }
        path.reverse()
        return path
    }

    fun migrate(
        steps: List<Step>,
        currentVersion: Int,
        targetVersion: Int,
        initialSettings: Map<String, String>,
    ): MigrationResult {
        val planned = plan(steps, currentVersion, targetVersion)
        // Defensive snapshot ensures initialSettings is never mutated if a step throws
        var working: Map<String, String> = LinkedHashMap(initialSettings)
        val applied = ArrayList<Pair<Int, Int>>(planned.size)
        for (st in planned) {
            working = LinkedHashMap(st.transform(working))
            applied.add(st.fromVersion to st.toVersion)
        }
        return MigrationResult(targetVersion, applied, working)
    }
}
