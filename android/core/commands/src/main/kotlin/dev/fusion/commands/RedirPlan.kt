// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * I/O-redirection planner over an already-split argv.
 *
 * Sits next to [PipePlan] (which splits stages) and [ArgSplit] (which
 * splits words): neither reads the `> >> < 2>` operators inside a stage.
 * This scans exact argv tokens — `>` truncate-out, `>>` append-out, `<`
 * stdin, `2>` truncate-err — each consuming the following token as its
 * target, and returns the clean argv plus the redirection list. Only
 * exact-token operators are recognized: a glued `2>file` stays a plain
 * argument (char-level lexing belongs to a lexer, not a planner).
 * Missing targets, operator-valued targets, and duplicate redirections of
 * the same stream are refused — real shells take last-wins, but a planner
 * that silently drops all but one `>` would hand the runner an argv whose
 * text promises two outputs.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object RedirPlan {

    /** Redirection streams. */
    enum class Stream { STDOUT, STDERR, STDIN }

    /** One redirection: append flag, stream, and target path token. */
    data class Redir(val stream: Stream, val append: Boolean, val target: String)

    /** Planned stage: clean argv plus redirections in source order. */
    data class Plan(val argv: List<String>, val redirs: List<Redir>)

    private val OPS = setOf(">", ">>", "<", "2>")

    /**
     * Plans redirections in [argv].
     *
     * @throws IllegalArgumentException on missing/operator targets or a
     * stream redirected twice.
     */
    fun parse(argv: List<String>): Plan {
        val clean = mutableListOf<String>()
        val redirs = mutableListOf<Redir>()
        val seen = mutableSetOf<Stream>()
        var i = 0
        while (i < argv.size) {
            val t = argv[i]
            if (t !in OPS) {
                clean.add(t)
                i++
                continue
            }
            require(i + 1 < argv.size) { "missing target" }
            val target = argv[i + 1]
            require(target !in OPS && target.isNotEmpty()) { "bad target" }
            val (stream, append) = when (t) {
                ">" -> Stream.STDOUT to false
                ">>" -> Stream.STDOUT to true
                "<" -> Stream.STDIN to false
                else -> Stream.STDERR to false
            }
            require(seen.add(stream)) { "dup $stream" }
            redirs.add(Redir(stream, append, target))
            i += 2
        }
        return Plan(clean, redirs)
    }
}
