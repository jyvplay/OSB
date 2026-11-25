// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Bounded bracket balancer for `()`, `[]`, `{}`.
 */
class EditorBracketBalancer private constructor() {
    companion object {
        fun isBalanced(text: String): Boolean {
            val stack = ArrayDeque<Char>()
            for (ch in text) {
                when (ch) {
                    '(', '[', '{' -> stack.addLast(ch)
                    ')' -> if (stack.isEmpty() || stack.removeLast() != '(') return false
                    ']' -> if (stack.isEmpty() || stack.removeLast() != '[') return false
                    '}' -> if (stack.isEmpty() || stack.removeLast() != '{') return false
                }
            }
            return stack.isEmpty()
        }
    }
}
