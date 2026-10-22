// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

/**
 * Reverse-Polish (postfix) stack evaluator over caller tokens.
 *
 * Sits next to [Calculator] (infix expressions): nothing evaluates the
 * postfix programs power users and macro recorders produce. Tokens are
 * decimal literals or the operators `+ - * /` (binary) and `neg sqrt`
 * (unary); evaluation is a single left-to-right stack pass. Division by
 * zero, stack underflow, unknown tokens, non-finite literals, and a final
 * stack holding anything but one value are all refused — an RPN program
 * that "evaluates" to whatever happened to be left on the stack would be
 * a silent miscomputation. Doubles throughout, so callers needing decimal
 * exactness must stay with [Calculator]; this kernel documents IEEE-754
 * semantics rather than hiding them.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object RpnEval {
    const val MAX_TOKENS = 1024

    /**
     * Evaluates [tokens] to a single Double.
     *
     * @throws IllegalArgumentException on unknown tokens, stack underflow,
     * division by zero, non-finite values, or a final stack size other
     * than one.
     */
    fun eval(tokens: List<String>): Double {
        require(tokens.size in 1..MAX_TOKENS) { "token budget" }
        val stack = ArrayDeque<Double>()
        for (t in tokens) {
            when (t) {
                "+", "-", "*", "/" -> {
                    require(stack.size >= 2) { "stack underflow" }
                    val b = stack.removeLast()
                    val a = stack.removeLast()
                    val r = when (t) {
                        "+" -> a + b
                        "-" -> a - b
                        "*" -> a * b
                        else -> {
                            require(b != 0.0) { "division by zero" }
                            a / b
                        }
                    }
                    require(r.isFinite()) { "non-finite result" }
                    stack.addLast(r)
                }
                "neg" -> {
                    require(stack.isNotEmpty()) { "stack underflow" }
                    stack.addLast(-stack.removeLast())
                }
                "sqrt" -> {
                    require(stack.isNotEmpty()) { "stack underflow" }
                    val a = stack.removeLast()
                    require(a >= 0.0) { "sqrt of negative" }
                    stack.addLast(kotlin.math.sqrt(a))
                }
                else -> {
                    val v = t.toDoubleOrNull()
                    require(v != null && v.isFinite()) { "bad literal $t" }
                    stack.addLast(v)
                }
            }
        }
        require(stack.size == 1) { "stack has ${stack.size} values" }
        return stack.single()
    }
}
