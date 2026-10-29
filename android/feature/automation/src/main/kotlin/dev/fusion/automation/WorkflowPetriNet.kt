// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * Clean-room **Bounded Place/Transition Petri-Net Workflow Engine & Reachability Safety Verifier**.
 *
 * Models concurrent automation workflows (`places`, weighted input/output `transitions`), executes
 * deterministic transition firings, detects deadlocked markings, and performs bounded BFS state-space
 * exploration verifying k-safety (`tokens <= kSafeCap`) and deadlock-freedom!
 */
object WorkflowPetriNet {
    const val SCOPE =
        "Bounded Place/Transition Petri-net workflow stepper, enabled-transition finder, deadlock detector, and BFS k-safety reachability verifier."

    data class Transition(
        val id: String,
        val consumes: Map<String, Int>,
        val produces: Map<String, Int>,
    ) {
        init {
            require(id.isNotBlank()) { "blank transition id" }
            require(consumes.values.all { it >= 1 } && produces.values.all { it >= 1 }) { "arc weights must be >= 1" }
        }
    }

    data class VerificationResult(
        val exploredStates: Int,
        val isKSafe: Boolean,
        val deadlockMarkings: List<Map<String, Int>>,
    )

    fun enabledTransitions(marking: Map<String, Int>, transitions: List<Transition>): List<Transition> =
        transitions.filter { t -> t.consumes.all { (place, w) -> (marking[place] ?: 0) >= w } }

    fun fire(marking: Map<String, Int>, transition: Transition): Map<String, Int> {
        for ((p, w) in transition.consumes) {
            require((marking[p] ?: 0) >= w) { "transition '${transition.id}' not enabled at place '$p'" }
        }
        val next = LinkedHashMap<String, Int>()
        for ((p, cnt) in marking) if (cnt > 0) next[p] = cnt
        for ((p, w) in transition.consumes) {
            val rem = (next[p] ?: 0) - w
            if (rem == 0) next.remove(p) else next[p] = rem
        }
        for ((p, w) in transition.produces) {
            next[p] = (next[p] ?: 0) + w
        }
        return next.entries.sortedBy { it.key }.associate { it.key to it.value }
    }

    fun verifyReachability(
        initialMarking: Map<String, Int>,
        transitions: List<Transition>,
        terminalPlaces: Set<String> = emptySet(),
        kSafeCap: Int = 8,
        maxStates: Int = 2_048,
    ): VerificationResult {
        val canonInit = initialMarking.filterValues { it > 0 }.entries.sortedBy { it.key }.associate { it.key to it.value }
        val visited = LinkedHashSet<Map<String, Int>>()
        val queue = ArrayDeque<Map<String, Int>>()
        val deadlocks = ArrayList<Map<String, Int>>()
        var kSafe = true

        visited.add(canonInit)
        queue.addLast(canonInit)

        while (queue.isNotEmpty() && visited.size <= maxStates) {
            val cur = queue.removeFirst()
            if (cur.values.any { it > kSafeCap }) kSafe = false
            val enabled = enabledTransitions(cur, transitions)
            if (enabled.isEmpty()) {
                val reachedTerminal = terminalPlaces.isNotEmpty() && terminalPlaces.any { (cur[it] ?: 0) > 0 }
                if (!reachedTerminal) deadlocks.add(cur)
            } else {
                for (t in enabled) {
                    val nxt = fire(cur, t)
                    if (visited.size < maxStates && visited.add(nxt)) {
                        queue.addLast(nxt)
                    }
                }
            }
        }
        return VerificationResult(visited.size, kSafe, deadlocks)
    }
}
