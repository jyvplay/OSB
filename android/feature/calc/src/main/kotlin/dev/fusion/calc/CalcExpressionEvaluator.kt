// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.pow

/**
 * Production-grade Shunting-Yard infix-to-RPN parser & evaluator.
 * Supports +, -, *, /, ^ (right-associative), parentheses, and unary minus.
 */
class CalcExpressionEvaluator private constructor() {
    companion object {
        private fun precedence(op: Char): Int = when (op) {
            '+', '-' -> 1
            '*', '/' -> 2
            '^' -> 3
            else -> 0
        }

        private fun isRightAssoc(op: Char): Boolean = op == '^'

        fun evaluate(expr: String): Double {
            val output = mutableListOf<String>()
            val ops = ArrayDeque<Char>()
            var i = 0
            var expectUnary = true

            while (i < expr.length) {
                val ch = expr[i]
                when {
                    ch.isWhitespace() -> i++
                    ch.isDigit() || ch == '.' || (ch == '-' && expectUnary) -> {
                        val start = i
                        if (ch == '-') i++
                        while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                        output.add(expr.substring(start, i))
                        expectUnary = false
                    }
                    ch == '(' -> {
                        ops.addLast(ch)
                        i++
                        expectUnary = true
                    }
                    ch == ')' -> {
                        while (ops.isNotEmpty() && ops.last() != '(') {
                            output.add(ops.removeLast().toString())
                        }
                        require(ops.isNotEmpty() && ops.removeLast() == '(') { "mismatched parentheses" }
                        i++
                        expectUnary = false
                    }
                    ch in "+-*/^" -> {
                        while (ops.isNotEmpty() && ops.last() != '(') {
                            val top = ops.last()
                            val pTop = precedence(top)
                            val pCur = precedence(ch)
                            if (pTop > pCur || (pTop == pCur && !isRightAssoc(ch))) {
                                output.add(ops.removeLast().toString())
                            } else break
                        }
                        ops.addLast(ch)
                        i++
                        expectUnary = true
                    }
                    else -> throw IllegalArgumentException("unexpected char: $ch")
                }
            }
            while (ops.isNotEmpty()) {
                val op = ops.removeLast()
                require(op != '(') { "mismatched parentheses" }
                output.add(op.toString())
            }

            val stack = ArrayDeque<Double>()
            for (tok in output) {
                if (tok.length == 1 && tok[0] in "+-*/^") {
                    require(stack.size >= 2) { "invalid expression" }
                    val b = stack.removeLast()
                    val a = stack.removeLast()
                    val res = when (tok[0]) {
                        '+' -> a + b
                        '-' -> a - b
                        '*' -> a * b
                        '/' -> {
                            require(b != 0.0) { "division by zero" }
                            a / b
                        }
                        '^' -> a.pow(b)
                        else -> error("unreachable")
                    }
                    stack.addLast(res)
                } else {
                    stack.addLast(tok.toDouble())
                }
            }
            require(stack.size == 1) { "invalid expression" }
            return stack.last()
        }
    }
}
