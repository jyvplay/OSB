// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

import java.util.Locale

/**
 * Clean-room boolean full-text & field-scoped query AST parser + evaluator (`BooleanQueryAst`).
 *
 * Powers `:feature:search` structured local queries across Notes, Documents, and Files:
 * - Operators: `AND` (and implicit whitespace conjunction), `OR` (lower precedence than `AND`),
 *   `NOT` / `-term` prefix negation, parenthesized grouping `(budget OR ledger) AND -draft`,
 *   `"exact phrase"` matching, and `field:value` filters (`ext:xlsx`, `tag:tax`, `title:report`).
 */
object BooleanQueryAst {
    const val SCOPE =
        "Recursive-descent boolean query AST parser & evaluator (AND/implicit-AND, OR, NOT/-negation, parentheses, quoted phrases, field:value filters); proximity NEAR/k operators out of scope."

    sealed interface Expr {
        data object MatchAll : Expr
        data class Term(val word: String) : Expr
        data class Phrase(val phrase: String) : Expr
        data class FieldEq(val field: String, val value: String) : Expr
        data class Not(val inner: Expr) : Expr
        data class And(val left: Expr, val right: Expr) : Expr
        data class Or(val left: Expr, val right: Expr) : Expr
    }

    data class SearchableDoc(
        val id: String,
        val body: String,
        val fields: Map<String, Set<String>> = emptyMap(),
    )

    fun parse(rawQuery: String): Expr {
        val tokens = tokenize(rawQuery)
        if (tokens.isEmpty()) return Expr.MatchAll
        val p = Parser(tokens)
        val ast = p.parseOr(0)
        require(p.pos == tokens.size) { "Unexpected token '${tokens[p.pos].text}' at position ${p.pos}" }
        return ast
    }

    fun matches(expr: Expr, doc: SearchableDoc): Boolean {
        val lowerBody = doc.body.lowercase(Locale.ROOT)
        val wordSet = Regex("[a-z0-9_]+").findAll(lowerBody).map { it.value }.toSet()
        val normFields = doc.fields.entries.associate { (k, vs) ->
            k.lowercase(Locale.ROOT) to vs.map { it.lowercase(Locale.ROOT) }.toSet()
        }

        fun eval(e: Expr): Boolean = when (e) {
            Expr.MatchAll -> true
            is Expr.Term -> e.word in wordSet || lowerBody.contains(e.word)
            is Expr.Phrase -> lowerBody.contains(e.phrase)
            is Expr.FieldEq -> normFields[e.field]?.any { it == e.value || it.contains(e.value) } == true
            is Expr.Not -> !eval(e.inner)
            is Expr.And -> eval(e.left) && eval(e.right)
            is Expr.Or -> eval(e.left) || eval(e.right)
        }
        return eval(expr)
    }

    private enum class TokKind { LPAREN, RPAREN, AND, OR, NOT, PHRASE, FIELD, WORD }
    private data class Tok(val kind: TokKind, val text: String, val extra: String = "")

    private fun tokenize(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) break
            when (s[i]) {
                '(' -> { out.add(Tok(TokKind.LPAREN, "(")); i++ }
                ')' -> { out.add(Tok(TokKind.RPAREN, ")")); i++ }
                '-' -> if (i + 1 < s.length && !s[i + 1].isWhitespace()) {
                    out.add(Tok(TokKind.NOT, "-"))
                    i++
                } else i++
                '"' -> {
                    val end = s.indexOf('"', i + 1)
                    require(end > i) { "Unterminated quote in search query" }
                    out.add(Tok(TokKind.PHRASE, s.substring(i + 1, end).lowercase(Locale.ROOT)))
                    i = end + 1
                }
                else -> {
                    val start = i
                    while (i < s.length && !s[i].isWhitespace() && s[i] != '(' && s[i] != ')') {
                        if (s[i] == '"') {
                            val endQ = s.indexOf('"', i + 1)
                            require(endQ > i) { "Unterminated field quote" }
                            i = endQ + 1
                        } else i++
                    }
                    val raw = s.substring(start, i)
                    val colon = raw.indexOf(':')
                    when {
                        raw == "AND" -> out.add(Tok(TokKind.AND, raw))
                        raw == "OR" -> out.add(Tok(TokKind.OR, raw))
                        raw == "NOT" -> out.add(Tok(TokKind.NOT, raw))
                        colon > 0 && colon < raw.lastIndex -> {
                            val fld = raw.substring(0, colon).lowercase(Locale.ROOT)
                            val fval = raw.substring(colon + 1).removeSurrounding("\"").lowercase(Locale.ROOT)
                            out.add(Tok(TokKind.FIELD, fld, fval))
                        }
                        else -> out.add(Tok(TokKind.WORD, raw.lowercase(Locale.ROOT)))
                    }
                }
            }
        }
        return out
    }

    private class Parser(private val toks: List<Tok>) {
        var pos = 0

        fun parseOr(depth: Int): Expr {
            require(depth <= 32) { "Query nesting exceeds depth 32" }
            var left = parseAnd(depth + 1)
            while (pos < toks.size && toks[pos].kind == TokKind.OR) {
                pos++
                left = Expr.Or(left, parseAnd(depth + 1))
            }
            return left
        }

        private fun parseAnd(depth: Int): Expr {
            var left = parseUnary(depth + 1)
            while (pos < toks.size) {
                val k = toks[pos].kind
                if (k == TokKind.OR || k == TokKind.RPAREN) break
                if (k == TokKind.AND) pos++
                left = Expr.And(left, parseUnary(depth + 1))
            }
            return left
        }

        private fun parseUnary(depth: Int): Expr {
            require(depth <= 36) { "Query nesting exceeds depth 36" }
            require(pos < toks.size) { "Unexpected end of query" }
            if (toks[pos].kind == TokKind.NOT) {
                pos++
                return Expr.Not(parseUnary(depth + 1))
            }
            val t = toks[pos++]
            return when (t.kind) {
                TokKind.LPAREN -> {
                    val inner = parseOr(depth + 1)
                    require(pos < toks.size && toks[pos].kind == TokKind.RPAREN) { "Unclosed '(' in query" }
                    pos++
                    inner
                }
                TokKind.PHRASE -> Expr.Phrase(t.text)
                TokKind.FIELD -> Expr.FieldEq(t.text, t.extra)
                TokKind.WORD -> Expr.Term(t.text)
                else -> throw IllegalArgumentException("Unexpected operator '${t.text}'")
            }
        }
    }
}
