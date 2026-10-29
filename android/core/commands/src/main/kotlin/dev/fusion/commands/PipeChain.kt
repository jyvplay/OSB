// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Safe, shell-free Unix-philosophy Command Pipeline Parser & Bounded In-Memory Executor.
 *
 * Parses pipelines like `grep -i error | sort -u | head -n 5` into typed [Stage] invocations
 * while **categorically refusing** shell command-injection metacharacters outside quotes
 * (`;`, `&&`, `||`, `&`, `` ` ``, `$(`, `>`, `<`, `\n`) so no untrusted argument can ever pivot
 * into subshell execution or arbitrary file redirection.
 */
object PipeChain {
    const val SCOPE =
        "Shell-free command pipeline parser and bounded string-stream executor (| stages, single/double quotes, injection-metachar refusal, max output cap); OS fork/exec processes out of scope."

    const val MAX_STAGES = 16
    const val MAX_OUTPUT_CHARS = 262_144

    data class Stage(val command: String, val args: List<String>) {
        init {
            require(command.matches(Regex("[a-zA-Z0-9._-]+"))) { "invalid command token '$command'" }
        }
    }

    fun interface StageHandler {
        fun execute(args: List<String>, stdin: String): String
    }

    fun parse(pipeline: String): List<Stage> {
        require(pipeline.isNotBlank() && pipeline.length <= 8_192) { "invalid pipeline length" }
        val stages = ArrayList<Stage>()
        val curArgs = ArrayList<String>()
        val tok = StringBuilder()
        var inToken = false
        var quote: Char = '\u0000'
        var i = 0

        fun flushToken() {
            if (inToken) {
                curArgs.add(tok.toString())
                tok.setLength(0)
                inToken = false
            }
        }

        fun flushStage() {
            flushToken()
            require(curArgs.isNotEmpty()) { "empty pipeline stage around '|' in '$pipeline'" }
            stages.add(Stage(curArgs.first(), curArgs.drop(1)))
            require(stages.size <= MAX_STAGES) { "pipeline exceeds $MAX_STAGES stages" }
            curArgs.clear()
        }

        while (i < pipeline.length) {
            val c = pipeline[i]
            if (quote != '\u0000') {
                if (c == '\\' && quote == '"' && i + 1 < pipeline.length) {
                    tok.append(pipeline[i + 1])
                    i += 2
                    continue
                }
                if (c == quote) {
                    quote = '\u0000'
                } else {
                    if (quote == '"' && (c == '`' || (c == '$' && i + 1 < pipeline.length && pipeline[i + 1] == '('))) {
                        throw IllegalArgumentException("command substitution refused inside double quotes")
                    }
                    tok.append(c)
                }
                i++
                continue
            }
            when (c) {
                '\'', '"' -> {
                    quote = c
                    inToken = true
                }
                ' ', '\t' -> flushToken()
                '|' -> {
                    require(i + 1 >= pipeline.length || pipeline[i + 1] != '|') {
                        "logical OR '||' refused in safe pipeline"
                    }
                    flushStage()
                }
                ';', '&', '`', '>', '<', '\n', '\r' ->
                    throw IllegalArgumentException("forbidden shell metacharacter '$c' refused")
                '$' -> {
                    require(i + 1 >= pipeline.length || pipeline[i + 1] != '(') {
                        "subshell '$(' refused in safe pipeline"
                    }
                    inToken = true
                    tok.append(c)
                }
                '\\' -> {
                    require(i + 1 < pipeline.length) { "trailing backslash in pipeline" }
                    inToken = true
                    tok.append(pipeline[i + 1])
                    i++
                }
                else -> {
                    inToken = true
                    tok.append(c)
                }
            }
            i++
        }
        require(quote == '\u0000') { "unclosed quote ($quote) in pipeline" }
        flushStage()
        return stages
    }

    fun execute(
        pipeline: String,
        initialInput: String,
        registry: Map<String, StageHandler>,
        maxOutputChars: Int = MAX_OUTPUT_CHARS,
    ): String {
        require(maxOutputChars in 1..MAX_OUTPUT_CHARS) { "invalid maxOutputChars $maxOutputChars" }
        require(initialInput.length <= maxOutputChars) { "initialInput exceeds $maxOutputChars chars" }
        val stages = parse(pipeline)
        var data = initialInput
        for (stage in stages) {
            val handler = registry[stage.command]
                ?: throw IllegalArgumentException("unregistered pipeline command '${stage.command}'")
            data = handler.execute(stage.args, data)
            require(data.length <= maxOutputChars) {
                "stage '${stage.command}' output (${data.length} chars) exceeded cap $maxOutputChars"
            }
        }
        return data
    }

    /** Built-in pure standard filter library (`grep`, `sort`, `uniq`, `head`, `wc`, `cut`). */
    fun standardFilters(): Map<String, StageHandler> = mapOf(
        "grep" to StageHandler { args, stdin ->
            val ignoreCase = "-i" in args
            val invert = "-v" in args
            val pattern = args.lastOrNull { !it.startsWith("-") }
                ?: throw IllegalArgumentException("grep requires pattern")
            if (stdin.isEmpty()) ""
            else stdin.lines().filter { line ->
                val hit = line.contains(pattern, ignoreCase = ignoreCase)
                if (invert) !hit else hit
            }.joinToString("\n")
        },
        "sort" to StageHandler { args, stdin ->
            if (stdin.isEmpty()) "" else {
                val rev = "-r" in args
                val uniq = "-u" in args
                var ls = stdin.lines().sorted()
                if (rev) ls = ls.reversed()
                if (uniq) ls = ls.distinct()
                ls.joinToString("\n")
            }
        },
        "uniq" to StageHandler { _, stdin ->
            if (stdin.isEmpty()) "" else {
                val out = ArrayList<String>()
                for (ln in stdin.lines()) {
                    if (out.isEmpty() || out.last() != ln) out.add(ln)
                }
                out.joinToString("\n")
            }
        },
        "head" to StageHandler { args, stdin ->
            val nIdx = args.indexOf("-n")
            val n = if (nIdx >= 0 && nIdx + 1 < args.size) args[nIdx + 1].toInt() else 10
            require(n >= 0) { "head -n must be >= 0" }
            if (stdin.isEmpty() || n == 0) "" else stdin.lines().take(n).joinToString("\n")
        },
        "wc" to StageHandler { args, stdin ->
            if ("-l" in args) {
                (if (stdin.isEmpty()) 0 else stdin.lines().size).toString()
            } else {
                stdin.length.toString()
            }
        },
    )
}
