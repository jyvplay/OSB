// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Bounded shell-style argument tokenizer supporting quotes and escapes.
 */
class CommandArgTokenizer private constructor() {
    companion object {
        fun tokenize(input: String): List<String> {
            val args = mutableListOf<String>()
            val cur = StringBuilder()
            var inSingle = false
            var inDouble = false
            var escaped = false
            var hasToken = false

            for (ch in input) {
                when {
                    escaped -> { cur.append(ch); escaped = false; hasToken = true }
                    ch == '\\' && !inSingle -> { escaped = true; hasToken = true }
                    ch == '\'' && !inDouble -> { inSingle = !inSingle; hasToken = true }
                    ch == '"' && !inSingle -> { inDouble = !inDouble; hasToken = true }
                    ch.isWhitespace() && !inSingle && !inDouble -> {
                        if (hasToken) {
                            args.add(cur.toString())
                            cur.setLength(0)
                            hasToken = false
                        }
                    }
                    else -> { cur.append(ch); hasToken = true }
                }
            }
            require(!inSingle && !inDouble && !escaped) { "unterminated quote or escape" }
            if (hasToken) args.add(cur.toString())
            return args
        }
    }
}
