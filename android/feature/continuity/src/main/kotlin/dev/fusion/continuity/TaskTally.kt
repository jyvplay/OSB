// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Status tallies over parsed VTODOs.
 *
 * Sits next to [VTodoParse] (which parses one block): nothing counts a
 * task list. [tally] walks caller-supplied [VTodoParse.Todo]s and counts
 * each RFC 5545 status; a null status is tallied as `unspecified`
 * (VTODOs without STATUS are legal and must not be silently filed under
 * NEEDS_ACTION). Due dates, priorities, and percents are outside — this
 * kernel counts statuses, nothing else.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object TaskTally {
    const val MAX_TODOS = 100_000

    /** Counts per status; [total] always equals the five buckets' sum. */
    data class Tally(
        val needsAction: Int,
        val inProgress: Int,
        val completed: Int,
        val cancelled: Int,
        val unspecified: Int,
        val total: Int,
    )

    /**
     * Tallies [todos] by status.
     *
     * @throws IllegalArgumentException over the list cap.
     */
    fun tally(todos: List<VTodoParse.Todo>): Tally {
        require(todos.size <= MAX_TODOS) { "too many todos" }
        var na = 0; var ip = 0; var co = 0; var ca = 0; var un = 0
        for (t in todos) {
            when (t.status) {
                VTodoParse.Status.NEEDS_ACTION -> na++
                VTodoParse.Status.IN_PROGRESS -> ip++
                VTodoParse.Status.COMPLETED -> co++
                VTodoParse.Status.CANCELLED -> ca++
                null -> un++
            }
        }
        return Tally(na, ip, co, ca, un, todos.size)
    }
}
