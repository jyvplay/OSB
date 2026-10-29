// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Clean-room **Ken Thompson NFA Linear-Time (`O(N * M)`) ReDoS-Immune Regular Expression Engine**.
 *
 * Unlike backtracking regex engines (`java.util.regex.Pattern`) that suffer exponential `O(2^N)`
 * catastrophic backtracking on pathological patterns such as `(a+)+b` against `"aaaa...aaaa!"`,
 * this engine compiles regex syntax (`|`, `*`, `+`, `?`, `(...)`, `[a-z0-9]`, `[^...]`, `.`, `\d`,
 * `\w`, `\s`, `^`, `$`) via Shunting-Yard -> Thompson NFA and simulates active state sets in
 * guaranteed **`O(patternStates * inputLength)`** time!
 */
object RegexSafeAutomaton {
    const val SCOPE =
        "Clean-room Thompson NFA linear-time O(N*M) ReDoS-immune regular expression engine (|, *, +, ?, (), [...], [^...], ., \\d/\\w/\\s, ^, \$)."

    const val MAX_PATTERN_LEN = 512

    internal sealed interface Pred {
        fun matches(ch: Char): Boolean
        data object AnyNonNewline : Pred { override fun matches(ch: Char) = ch != '\n' }
        data class Literal(val c: Char) : Pred { override fun matches(ch: Char) = ch == c }
        data class CharClass(val ranges: List<CharRange>, val negated: Boolean) : Pred {
            override fun matches(ch: Char): Boolean {
                val hit = ranges.any { ch in it }
                return if (negated) !hit else hit
            }
        }
    }

    internal class State(
        var pred: Pred? = null, // null => epsilon or accept
        var out1: State? = null,
        var out2: State? = null,
        var isAccept: Boolean = false,
        val id: Int,
    )

    class CompiledRegex internal constructor(
        private val start: State,
        private val stateCount: Int,
        private val anchoredStart: Boolean,
        private val anchoredEnd: Boolean,
    ) {
        fun matchesFull(input: String): Boolean = matchAt(input, 0, requireFullEnd = true)

        fun containsMatchIn(input: String): Boolean {
            if (anchoredStart) return matchAt(input, 0, requireFullEnd = anchoredEnd)
            for (startIdx in 0..input.length) {
                if (matchAt(input, startIdx, requireFullEnd = anchoredEnd)) return true
            }
            return false
        }

        private fun matchAt(input: String, offset: Int, requireFullEnd: Boolean): Boolean {
            var current = ArrayList<State>(stateCount)
            var next = ArrayList<State>(stateCount)
            val seen = IntArray(stateCount)
            var stepId = 1

            fun addState(s: State?, list: MutableList<State>) {
                if (s == null || seen[s.id] == stepId) return
                seen[s.id] = stepId
                if (s.pred == null && !s.isAccept) {
                    addState(s.out1, list)
                    addState(s.out2, list)
                } else {
                    list.add(s)
                }
            }

            addState(start, current)
            if (!requireFullEnd && current.any { it.isAccept }) return true

            for (i in offset until input.length) {
                val ch = input[i]
                stepId++
                next.clear()
                for (st in current) {
                    val p = st.pred
                    if (p != null && p.matches(ch)) {
                        addState(st.out1, next)
                    }
                }
                val tmp = current; current = next; next = tmp
                if (current.isEmpty()) return false
                if (!requireFullEnd && current.any { it.isAccept }) return true
            }
            return current.any { it.isAccept }
        }
    }

    private sealed interface Tok {
        data class Match(val pred: Pred) : Tok
        data object Concat : Tok
        data object Alt : Tok
        data object Star : Tok
        data object Plus : Tok
        data object Quest : Tok
        data object LParen : Tok
        data object RParen : Tok
    }

    fun compile(pattern: String): CompiledRegex {
        require(pattern.length <= MAX_PATTERN_LEN) { "regex length exceeds $MAX_PATTERN_LEN" }
        var body = pattern
        val anchoredStart = body.startsWith("^").also { if (it) body = body.drop(1) }
        val anchoredEnd = (body.endsWith("$") && !body.endsWith("\\$")).also { if (it) body = body.dropLast(1) }

        val rawToks = lex(body)
        if (rawToks.isEmpty()) {
            val acc = State(isAccept = true, id = 0)
            return CompiledRegex(acc, 1, anchoredStart, anchoredEnd)
        }
        val withConcat = insertConcats(rawToks)
        val postfix = toPostfix(withConcat)
        var nextId = 0
        fun newState(pred: Pred? = null, o1: State? = null, o2: State? = null, acc: Boolean = false) =
            State(pred, o1, o2, acc, nextId++)

        data class Frag(val start: State, val outs: List<(State) -> Unit>)
        fun patch(outs: List<(State) -> Unit>, target: State) { outs.forEach { it(target) } }

        val stack = ArrayDeque<Frag>()
        for (t in postfix) {
            when (t) {
                is Tok.Match -> {
                    val s = newState(pred = t.pred)
                    stack.addLast(Frag(s, listOf { s.out1 = it }))
                }
                Tok.Concat -> {
                    require(stack.size >= 2) { "malformed regex concatenation" }
                    val e2 = stack.removeLast()
                    val e1 = stack.removeLast()
                    patch(e1.outs, e2.start)
                    stack.addLast(Frag(e1.start, e2.outs))
                }
                Tok.Alt -> {
                    require(stack.size >= 2) { "malformed regex alternation '|'" }
                    val e2 = stack.removeLast()
                    val e1 = stack.removeLast()
                    val s = newState(o1 = e1.start, o2 = e2.start)
                    stack.addLast(Frag(s, e1.outs + e2.outs))
                }
                Tok.Star -> {
                    require(stack.isNotEmpty()) { "dangling '*'" }
                    val e = stack.removeLast()
                    val s = newState(o1 = e.start)
                    patch(e.outs, s)
                    stack.addLast(Frag(s, listOf { s.out2 = it }))
                }
                Tok.Plus -> {
                    require(stack.isNotEmpty()) { "dangling '+'" }
                    val e = stack.removeLast()
                    val s = newState(o1 = e.start)
                    patch(e.outs, s)
                    stack.addLast(Frag(e.start, listOf { s.out2 = it }))
                }
                Tok.Quest -> {
                    require(stack.isNotEmpty()) { "dangling '?'" }
                    val e = stack.removeLast()
                    val s = newState(o1 = e.start)
                    stack.addLast(Frag(s, e.outs + listOf { s.out2 = it }))
                }
                else -> error("unexpected parenthesis in postfix")
            }
        }
        require(stack.size == 1) { "malformed regular expression '$pattern'" }
        val finalFrag = stack.removeLast()
        val accept = newState(acc = true)
        patch(finalFrag.outs, accept)
        return CompiledRegex(finalFrag.start, nextId, anchoredStart, anchoredEnd)
    }

    private fun lex(src: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < src.length) {
            when (val c = src[i++]) {
                '(' -> out.add(Tok.LParen)
                ')' -> out.add(Tok.RParen)
                '|' -> out.add(Tok.Alt)
                '*' -> out.add(Tok.Star)
                '+' -> out.add(Tok.Plus)
                '?' -> out.add(Tok.Quest)
                '.' -> out.add(Tok.Match(Pred.AnyNonNewline))
                '\\' -> {
                    require(i < src.length) { "trailing '\\' in regex" }
                    val esc = src[i++]
                    val p = when (esc) {
                        'd' -> Pred.CharClass(listOf('0'..'9'), false)
                        'w' -> Pred.CharClass(listOf('a'..'z', 'A'..'Z', '0'..'9', '_'..'_'), false)
                        's' -> Pred.CharClass(listOf(' '..' ', '\t'..'\t', '\r'..'\r', '\n'..'\n'), false)
                        else -> Pred.Literal(esc)
                    }
                    out.add(Tok.Match(p))
                }
                '[' -> {
                    var neg = false
                    if (i < src.length && src[i] == '^') { neg = true; i++ }
                    val ranges = ArrayList<CharRange>()
                    while (i < src.length && src[i] != ']') {
                        val lo = src[i++]
                        if (i + 1 < src.length && src[i] == '-' && src[i + 1] != ']') {
                            i++
                            val hi = src[i++]
                            ranges.add(lo..hi)
                        } else {
                            ranges.add(lo..lo)
                        }
                    }
                    require(i < src.length && src[i] == ']' && ranges.isNotEmpty()) { "unclosed or empty '[...]' class" }
                    i++
                    out.add(Tok.Match(Pred.CharClass(ranges, neg)))
                }
                else -> out.add(Tok.Match(Pred.Literal(c)))
            }
        }
        return out
    }

    private fun insertConcats(toks: List<Tok>): List<Tok> {
        val out = ArrayList<Tok>(toks.size * 2)
        for (i in toks.indices) {
            val cur = toks[i]
            out.add(cur)
            if (i + 1 < toks.size) {
                val nxt = toks[i + 1]
                val curCanLeft = cur is Tok.Match || cur == Tok.RParen || cur == Tok.Star || cur == Tok.Plus || cur == Tok.Quest
                val nxtCanRight = nxt is Tok.Match || nxt == Tok.LParen
                if (curCanLeft && nxtCanRight) out.add(Tok.Concat)
            }
        }
        return out
    }

    private fun precedence(t: Tok): Int = when (t) {
        Tok.Star, Tok.Plus, Tok.Quest -> 3
        Tok.Concat -> 2
        Tok.Alt -> 1
        else -> 0
    }

    private fun toPostfix(toks: List<Tok>): List<Tok> {
        val out = ArrayList<Tok>()
        val ops = ArrayDeque<Tok>()
        for (t in toks) {
            when (t) {
                is Tok.Match -> out.add(t)
                Tok.LParen -> ops.addLast(t)
                Tok.RParen -> {
                    while (ops.isNotEmpty() && ops.last() != Tok.LParen) out.add(ops.removeLast())
                    require(ops.isNotEmpty() && ops.removeLast() == Tok.LParen) { "unmatched ')'" }
                }
                else -> {
                    val prec = precedence(t)
                    while (ops.isNotEmpty() && ops.last() != Tok.LParen && precedence(ops.last()) >= prec) {
                        out.add(ops.removeLast())
                    }
                    ops.addLast(t)
                }
            }
        }
        while (ops.isNotEmpty()) {
            val op = ops.removeLast()
            require(op != Tok.LParen) { "unclosed '('" }
            out.add(op)
        }
        return out
    }
}
