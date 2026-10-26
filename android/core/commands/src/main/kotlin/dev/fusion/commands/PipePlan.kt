// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Pipeline splitter for shell command lines.
 *
 * Sits next to [ArgSplit] (which splits one command into words): nothing splits
 * `a | b | c` into its stages. A `|` inside single or double quotes is literal
 * text, not a stage boundary; a backslash escapes the next character everywhere
 * except inside single quotes. Each stage is trimmed and must be non-blank, so
 * leading, trailing, and doubled pipes are refused rather than planned as
 * no-op stages. Unterminated quotes and a trailing lone backslash are refused
 * for the same reason [ArgSplit] refuses them: planning a command the
 * word-splitter cannot lex would hand the runner a stage it can never execute.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object PipePlan {

    /**
     * Splits [line] into pipeline stages. Always returns at least one stage;
     * a line without a bare `|` yields the single trimmed command.
     *
     * @throws IllegalArgumentException on empty stages, unterminated quotes,
     * or a trailing backslash.
     */
    fun split(line: String): List<String> {
        val stages = mutableListOf<String>()
        val cur = StringBuilder()
        var single = false
        var double = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && !single -> {
                    require(i + 1 < line.length) { "trailing backslash" }
                    cur.append(c).append(line[i + 1])
                    i += 2
                }
                c == '\'' && !double -> { single = !single; cur.append(c); i++ }
                c == '"' && !single -> { double = !double; cur.append(c); i++ }
                c == '|' && !single && !double -> {
                    val stage = cur.toString().trim()
                    require(stage.isNotEmpty()) { "empty stage" }
                    stages += stage
                    cur.clear()
                    i++
                }
                else -> { cur.append(c); i++ }
            }
        }
        require(!single && !double) { "unterminated quote" }
        val last = cur.toString().trim()
        require(last.isNotEmpty()) { "empty stage" }
        stages += last
        return stages
    }
}
