package dev.fusion.together

/**
 * Deterministic turn rotation for shared sessions (stated contract, not
 * an imported spec — the rules below ARE the specification, and the
 * tests execute them):
 *
 * 1. Participants keep their join order; the order never reshuffles.
 * 2. [next] advances to the first non-skipped participant strictly
 *    after the current one, wrapping at the end.
 * 3. Completing a wrap (passing the end of the list) increments the
 *    round counter exactly once.
 * 4. Skipped participants stay in the list (they can be unskipped);
 *    a state where EVERYONE is skipped refuses — there is no turn to
 *    give, and inventing one would desynchronize peers.
 *
 * Declared narrowings: pure rotation math — no networking, no clocks
 * (Lamport/Peer clocks are the session layer's kernels), no identity.
 */
object TurnOrder {
    const val SCOPE = "Pure rotation with skip-set and round counting; sessions/clocks/identity live elsewhere."

    data class State(val participants: List<String>, val currentIndex: Int, val round: Int, val skipped: Set<String>) {
        init {
            require(participants.isNotEmpty()) { "no participants" }
            require(participants.toSet().size == participants.size) { "duplicate participant names" }
            require(currentIndex in participants.indices) { "currentIndex $currentIndex out of range" }
            require(round >= 0) { "negative round" }
            require(skipped.all { it in participants }) { "skip-set names unknown participants" }
        }
        val current: String get() = participants[currentIndex]
    }

    fun start(participants: List<String>): State = State(participants, 0, 0, emptySet())

    fun next(s: State): State {
        require(s.participants.any { it !in s.skipped }) { "everyone is skipped — no turn exists; unskip first" }
        var i = s.currentIndex
        var round = s.round
        do {
            i++
            if (i == s.participants.size) { i = 0; round++ }
        } while (s.participants[i] in s.skipped)
        return s.copy(currentIndex = i, round = round)
    }

    fun skip(s: State, name: String): State {
        require(name in s.participants) { "'$name' is not in this session" }
        return s.copy(skipped = s.skipped + name)
    }

    fun unskip(s: State, name: String): State {
        require(name in s.participants) { "'$name' is not in this session" }
        return s.copy(skipped = s.skipped - name)
    }

    /** Turns until [name] comes up, counting from the state's current turn holder's NEXT turn. */
    fun distanceTo(s: State, name: String): Int {
        require(name in s.participants && name !in s.skipped) { "'$name' absent or skipped" }
        var state = s
        var steps = 1
        while (true) {
            state = next(state)
            if (state.current == name) return steps
            steps++
            require(steps <= s.participants.size) { "unreachable: rotation failed to find '$name'" }
        }
    }
}
