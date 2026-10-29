package dev.fusion.terminal

/**
 * Clean-room POSIX Shell Pipeline Lexer & Command-Substitution / Unquoted-Metacharacter Safety
 * Analyzer (Apache-2.0).
 */
object ShellPipelineTokenizer {
    const val SCOPE =
        "POSIX single/double/backslash shell tokenizer + pipeline stage splitter + command-substitution safety gate"

    data class ParsedPipeline(
        val stages: List<List<String>>,
        val hasDangerousSubshellExpansion: Boolean,
    )

    fun parsePipeline(commandLine: String): ParsedPipeline {
        val stages = mutableListOf<List<String>>()
        var curArgs = mutableListOf<String>()
        val curToken = StringBuilder()
        var inSingle = false
        var inDouble = false
        var hasSubshell = false
        var i = 0

        fun flushToken() {
            if (curToken.isNotEmpty()) {
                curArgs.add(curToken.toString())
                curToken.setLength(0)
            }
        }

        while (i < commandLine.length) {
            val ch = commandLine[i]
            when {
                inSingle -> {
                    if (ch == '\'') inSingle = false else curToken.append(ch)
                }
                inDouble -> {
                    when (ch) {
                        '"' -> inDouble = false
                        '`' -> { hasSubshell = true; curToken.append(ch) }
                        '$' -> {
                            if (i + 1 < commandLine.length && commandLine[i + 1] == '(') hasSubshell = true
                            curToken.append(ch)
                        }
                        '\\' -> if (i + 1 < commandLine.length) { i++; curToken.append(commandLine[i]) } else curToken.append(ch)
                        else -> curToken.append(ch)
                    }
                }
                else -> {
                    when {
                        ch.isWhitespace() -> flushToken()
                        ch == '\'' -> inSingle = true
                        ch == '"' -> inDouble = true
                        ch == '\\' -> {
                            require(i + 1 < commandLine.length) { "Trailing backslash in shell command" }
                            i++; curToken.append(commandLine[i])
                        }
                        ch == '`' -> { hasSubshell = true; curToken.append(ch) }
                        ch == '$' && i + 1 < commandLine.length && commandLine[i + 1] == '(' -> {
                            hasSubshell = true; curToken.append(ch)
                        }
                        ch == '|' -> {
                            flushToken()
                            require(curArgs.isNotEmpty()) { "Empty pipeline stage before '|'" }
                            stages.add(curArgs)
                            curArgs = mutableListOf()
                        }
                        else -> curToken.append(ch)
                    }
                }
            }
            i++
        }
        require(!inSingle && !inDouble) { "Unterminated quote in shell command" }
        flushToken()
        if (curArgs.isNotEmpty()) stages.add(curArgs)
        return ParsedPipeline(stages, hasSubshell)
    }
}
