package dev.fusion.find

import java.util.Locale

/**
 * Clean-room Recursive-Descent Boolean & Structured-Predicate File/Workspace Search Query AST
 * Evaluator (`AND`, `OR`, `NOT` / `-term`, `ext:xlsx`, `size:>1024`, `"exact phrase"`) (Apache-2.0).
 */
object BooleanQueryAstEvaluator {
    const val SCOPE =
        "Recursive-descent Boolean query AST parser/evaluator (AND, OR, NOT/-neg, ext:, size:>/<, quoted phrases)"

    data class SearchableRecord(
        val id: String,
        val name: String,
        val extension: String,
        val byteSize: Long,
        val content: String,
    )

    sealed interface QueryNode {
        data class Term(val text: String) : QueryNode
        data class ExtFilter(val ext: String) : QueryNode
        data class SizeFilter(val op: String, val bytes: Long) : QueryNode
        data class Not(val child: QueryNode) : QueryNode
        data class And(val left: QueryNode, val right: QueryNode) : QueryNode
        data class Or(val left: QueryNode, val right: QueryNode) : QueryNode
    }

    fun parse(query: String): QueryNode {
        val tokens = tokenize(query)
        require(tokens.isNotEmpty()) { "Empty search query" }
        return RecursiveParser(tokens).parseFull()
    }

    private class RecursiveParser(private val tokens: List<String>) {
        private var pos = 0

        fun parseFull(): QueryNode {
            val ast = parseOr()
            require(pos == tokens.size) { "Unexpected trailing token '${tokens[pos]}'" }
            return ast
        }

        private fun parseOr(): QueryNode {
            var left = parseAnd()
            while (pos < tokens.size && tokens[pos].equals("OR", ignoreCase = true)) {
                pos++
                left = QueryNode.Or(left, parseAnd())
            }
            return left
        }

        private fun parseAnd(): QueryNode {
            var left = parseUnary()
            while (pos < tokens.size && !tokens[pos].equals("OR", ignoreCase = true) && tokens[pos] != ")") {
                if (tokens[pos].equals("AND", ignoreCase = true)) pos++
                left = QueryNode.And(left, parseUnary())
            }
            return left
        }

        private fun parseUnary(): QueryNode {
            if (tokens[pos].equals("NOT", ignoreCase = true)) {
                pos++
                return QueryNode.Not(parseUnary())
            }
            if (tokens[pos].startsWith("-") && tokens[pos].length > 1) {
                val stripped = tokens[pos++].substring(1)
                return QueryNode.Not(atomFromToken(stripped))
            }
            if (tokens[pos] == "(") {
                pos++
                val inner = parseOr()
                require(pos < tokens.size && tokens[pos] == ")") { "Unclosed '(' in query" }
                pos++
                return inner
            }
            return atomFromToken(tokens[pos++])
        }
    }

    fun matches(ast: QueryNode, rec: SearchableRecord): Boolean = when (ast) {
        is QueryNode.Term -> {
            val needle = ast.text.lowercase(Locale.ROOT)
            rec.name.lowercase(Locale.ROOT).contains(needle) || rec.content.lowercase(Locale.ROOT).contains(needle)
        }
        is QueryNode.ExtFilter -> rec.extension.trimStart('.').equals(ast.ext, ignoreCase = true)
        is QueryNode.SizeFilter -> when (ast.op) {
            ">" -> rec.byteSize > ast.bytes
            ">=" -> rec.byteSize >= ast.bytes
            "<" -> rec.byteSize < ast.bytes
            else -> rec.byteSize <= ast.bytes
        }
        is QueryNode.Not -> !matches(ast.child, rec)
        is QueryNode.And -> matches(ast.left, rec) && matches(ast.right, rec)
        is QueryNode.Or -> matches(ast.left, rec) || matches(ast.right, rec)
    }

    private fun atomFromToken(tok: String): QueryNode {
        val lower = tok.lowercase(Locale.ROOT)
        if (lower.startsWith("ext:")) return QueryNode.ExtFilter(lower.removePrefix("ext:").trimStart('.'))
        val sizeMatch = Regex("^size:(>=|<=|>|<)([0-9]+)(kb|mb)?$", RegexOption.IGNORE_CASE).matchEntire(tok)
        if (sizeMatch != null) {
            val mult = when (sizeMatch.groupValues[3].lowercase()) {
                "kb" -> 1024L
                "mb" -> 1024L * 1024L
                else -> 1L
            }
            return QueryNode.SizeFilter(sizeMatch.groupValues[1], sizeMatch.groupValues[2].toLong() * mult)
        }
        return QueryNode.Term(tok.removeSurrounding("\""))
    }

    private fun tokenize(q: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < q.length) {
            when {
                q[i].isWhitespace() -> i++
                q[i] == '(' || q[i] == ')' -> { out.add(q[i].toString()); i++ }
                q[i] == '"' -> {
                    val end = q.indexOf('"', i + 1)
                    require(end > i) { "Unterminated quote in search query" }
                    out.add(q.substring(i, end + 1))
                    i = end + 1
                }
                else -> {
                    val start = i
                    while (i < q.length && !q[i].isWhitespace() && q[i] != '(' && q[i] != ')') i++
                    out.add(q.substring(start, i))
                }
            }
        }
        return out
    }
}
