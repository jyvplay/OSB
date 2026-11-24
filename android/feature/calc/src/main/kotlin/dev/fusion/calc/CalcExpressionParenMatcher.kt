// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

/**
 * Bounded expression parenthesis/bracket/brace matcher. Validates balanced
 * delimiters `()`, `[]`, `{}` in calculator/spreadsheet expressions while
 * ignoring delimiters inside `"..."` string literals (with `\"` escapes).
 */
class CalcExpressionParenMatcher private constructor() {
    companion object {
        const val MAX_EXPR_LEN = 8192

        fun isBalanced(expr: String): Boolean {
            require(expr.length <= MAX_EXPR_LEN) { "expression too long" }
            val stack = ArrayDeque<Char>()
            var inString = false
            var escaped = false
            for (ch in expr) {
                if (inString) {
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '"') {
                        inString = false
                    }
                    continue
                }
                when (ch) {
                    '"' -> inString = true
                    '(', '[', '{' -> stack.addLast(ch)
                    ')' -> if (stack.isEmpty() || stack.removeLast() != '(') return false
                    ']' -> if (stack.isEmpty() || stack.removeLast() != '[') return false
                    '}' -> if (stack.isEmpty() || stack.removeLast() != '{') return false
                }
            }
            return !inString && stack.isEmpty()
        }
    }
}
