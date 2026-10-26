// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Shell-ish argv splitter feeding alias expansion: whitespace separation,
 * single quotes (literal, no escapes), double quotes (backslash escapes),
 * backslash escapes outside quotes, `#` comments outside quotes. No
 * nesting, no variable expansion, no globbing — refused shapes throw.
 * Caps: 8 KiB input, 256 args.
 */
object ArgSplit {
    const val MAX_CHARS = 8192
    const val MAX_ARGS = 256

    fun split(line: String): List<String> {
        require(line.length <= MAX_CHARS) { "line over $MAX_CHARS chars" }
        val args = mutableListOf<String>()
        val cur = StringBuilder()
        var inArg = false
        var quote = '\u0000'
        var i = 0
        fun flush() {
            if (inArg) {
                require(args.size < MAX_ARGS) { "over $MAX_ARGS args" }
                args += cur.toString()
                cur.clear()
                inArg = false
            }
        }
        while (i < line.length) {
            val c = line[i]
            if (quote == '\'') {
                if (c == '\'') quote = '\u0000' else cur.append(c)
                i++
                continue
            }
            if (quote == '"') {
                if (c == '"') {
                    quote = '\u0000'; i++; continue
                }
                if (c == '\\') {
                    require(i + 1 < line.length) { "trailing backslash in quotes" }
                    cur.append(line[i + 1]); i += 2; continue
                }
                cur.append(c); i++
                continue
            }
            when {
                c == '\'' || c == '"' -> { quote = c; inArg = true; i++ }
                c == '\\' -> {
                    require(i + 1 < line.length) { "trailing backslash" }
                    cur.append(line[i + 1]); inArg = true; i += 2
                }
                c == '#' -> break
                c.isWhitespace() -> { flush(); i++ }
                else -> { cur.append(c); inArg = true; i++ }
            }
        }
        require(quote == '\u0000') { "unterminated quote" }
        flush()
        return args
    }
}
