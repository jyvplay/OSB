// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

import java.util.Locale

/** Parsed boolean query. Terms/phrases are stored lowercased (ROOT). */
sealed interface QueryNode {
    data class Term(val text: String) : QueryNode
    data class Phrase(val text: String) : QueryNode
    data class Not(val node: QueryNode) : QueryNode
    data class And(val nodes: List<QueryNode>) : QueryNode
    data class Or(val nodes: List<QueryNode>) : QueryNode
}

/**
 * Small boolean query language over document text: bare terms (implicit
 * AND), double-quoted phrases, `-` negation, explicit OR, parentheses.
 * Precedence: NOT > AND > OR. `OR` is uppercase-only (a lowercase "or" is a
 * term); `-` negates anywhere, so hyphenated words must be quoted phrases.
 * Evaluation tokenizes the document the same way the query was tokenized,
 * so matching is self-contained. Caps: 256 query chars, 16 terms, nesting
 * depth 8. Wiring into LocalIndex.search is future work; the local scorer
 * keeps its own semantics.
 */
object SearchQuery {
    const val MAX_QUERY = 256
    const val MAX_TERMS = 16
    const val MAX_DEPTH = 8

    fun parse(query: String): QueryNode {
        require(query.length <= MAX_QUERY) { "query over $MAX_QUERY chars" }
        val p = Parser(query)
        val node = p.parseOr(0)
        p.end()
        var terms = 0
        fun count(n: QueryNode) {
            when (n) {
                is QueryNode.Term, is QueryNode.Phrase -> terms++
                is QueryNode.Not -> count(n.node)
                is QueryNode.And -> n.nodes.forEach(::count)
                is QueryNode.Or -> n.nodes.forEach(::count)
            }
        }
        count(node)
        require(terms in 1..MAX_TERMS) { "term count $terms outside 1..$MAX_TERMS" }
        return node
    }

    /** True when [docText] satisfies [node]. Lowercases with ROOT internally. */
    fun matches(node: QueryNode, docText: String): Boolean {
        val terms = tokenize(docText)
        val lowered = docText.lowercase(Locale.ROOT)
        fun eval(n: QueryNode): Boolean = when (n) {
            is QueryNode.Term -> n.text in terms
            is QueryNode.Phrase -> lowered.contains(n.text)
            is QueryNode.Not -> !eval(n.node)
            is QueryNode.And -> n.nodes.all(::eval)
            is QueryNode.Or -> n.nodes.any(::eval)
        }
        return eval(node)
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()

    private class Parser(private val s: String) {
        private var i = 0
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun end() { skip(); require(i == s.length) { "trailing input at $i" } }
        fun parseOr(depth: Int): QueryNode {
            require(depth <= MAX_DEPTH) { "nesting over $MAX_DEPTH" }
            val parts = mutableListOf(parseAnd(depth))
            while (true) {
                skip()
                if (s.startsWith("OR", i) && (i + 2 == s.length || !s[i + 2].isLetter())) {
                    i += 2
                    parts += parseAnd(depth)
                } else return if (parts.size == 1) parts[0] else QueryNode.Or(parts)
            }
        }
        private fun parseAnd(depth: Int): QueryNode {
            val parts = mutableListOf<QueryNode>()
            while (true) {
                skip()
                if (i >= s.length || s[i] == ')' ||
                    (s.startsWith("OR", i) && (i + 2 == s.length || !s[i + 2].isLetter()))
                ) break
                parts += parseNot(depth)
            }
            require(parts.isNotEmpty()) { "empty group at $i" }
            return if (parts.size == 1) parts[0] else QueryNode.And(parts)
        }
        private fun parseNot(depth: Int): QueryNode {
            skip()
            if (i < s.length && s[i] == '-') {
                i++
                skip()
                require(i < s.length && s[i] != ')') { "dangling NOT at $i" }
                return QueryNode.Not(parseNot(depth))
            }
            return parseAtom(depth)
        }
        private fun parseAtom(depth: Int): QueryNode {
            skip()
            require(i < s.length) { "expected term at $i" }
            if (s[i] == '(') {
                i++
                val inner = parseOr(depth + 1)
                skip()
                require(i < s.length && s[i] == ')') { "missing ) at $i" }
                i++
                return inner
            }
            if (s[i] == '"') {
                i++
                val start = i
                while (i < s.length && s[i] != '"') i++
                require(i < s.length) { "unterminated phrase" }
                val phrase = s.substring(start, i).lowercase(Locale.ROOT)
                require(phrase.isNotBlank()) { "empty phrase" }
                i++
                return QueryNode.Phrase(phrase)
            }
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit())) i++
            require(i > start) { "expected term at $i" }
            return QueryNode.Term(s.substring(start, i).lowercase(Locale.ROOT))
        }
    }
}
