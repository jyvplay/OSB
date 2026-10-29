// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Pure-JVM **Mail-Merge & Conditional Section Engine** for Writer (`:feature:writer`),
 * expanding `{{field}}` placeholders, `{{#if cond}}...{{#else}}...{{/if}}` conditional blocks,
 * and `{{#each items}}...{{/each}}` table-row repetitions with strict unresolved-field diagnostics.
 */
object WriterMailMergeEngine {
    const val SCOPE =
        "Writer mail-merge & conditional template expander ({{field}}, {{#if}}, {{#each}}) with missing-column diagnostics and multi-record batch pagination."

    data class MergeDocumentResult(
        val recordIndex: Int,
        val renderedBody: String,
        val unresolvedPlaceholders: Set<String>,
    )

    private const val MAX_CHARS = 2 * 1024 * 1024
    private const val MAX_DEPTH = 32
    private sealed interface Node {
        data class Text(val value: String) : Node
        data class Field(val key: String) : Node
        data class Block(val key: String, val each: Boolean, val yes: List<Node>, val no: List<Node>) : Node
    }

    // Parse template syntax once; substituted data is NEVER parsed as template syntax.
    private class Parser(val source: String) {
        var offset = 0
        var terminator: String? = null
        fun nodes(depth: Int = 0): List<Node> {
            require(depth <= MAX_DEPTH) { "Template nesting exceeds $MAX_DEPTH" }
            val out = ArrayList<Node>()
            while (offset < source.length) {
                val start = source.indexOf("{{", offset)
                if (start < 0) { out.add(Node.Text(source.substring(offset))); offset = source.length; break }
                if (start > offset) out.add(Node.Text(source.substring(offset, start)))
                val end = source.indexOf("}}", start + 2)
                require(end >= 0) { "Unclosed template token at $start" }
                val token = source.substring(start + 2, end).trim()
                offset = end + 2
                if (token == "#else" || token.startsWith("/")) {
                    require(depth > 0) { "Unexpected $token" }
                    terminator = token
                    return out
                }
                val each = token.startsWith("#each ")
                if (each || token.startsWith("#if ")) {
                    val key = token.substringAfter(' ').trim()
                    require(key.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid block key" }
                    terminator = null
                    val yes = nodes(depth + 1)
                    val no = if (!each && terminator == "#else") {
                        terminator = null
                        nodes(depth + 1)
                    } else emptyList()
                    require(terminator == if (each) "/each" else "/if") { "Mismatched or unclosed block: $key" }
                    terminator = null
                    out.add(Node.Block(key, each, yes, no))
                } else {
                    require(token.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid template token: $token" }
                    out.add(Node.Field(token))
                }
            }
            return out
        }
    }

    private fun truthy(raw: Any?): Boolean = when (raw) {
        null -> false
        is Boolean -> raw
        is Number -> raw.toDouble().isFinite() && raw.toDouble() != 0.0
        is String -> raw.isNotBlank() && !raw.equals("false", true) && raw != "0"
        is Collection<*> -> raw.isNotEmpty()
        else -> true
    }

    private fun parse(template: String): List<Node> {
        require(template.length <= MAX_CHARS) { "Template too large" }
        return Parser(template).nodes()
    }

    private fun render(index: Int, nodes: List<Node>, row: Map<String, Any?>): MergeDocumentResult {
        val unresolved = linkedSetOf<String>()
        val out = StringBuilder()
        var visits = 0
        fun append(text: String) {
            require(text.length <= MAX_CHARS - out.length) { "Merged document exceeds $MAX_CHARS UTF-16 units" }
            out.append(text)
        }
        fun emit(list: List<Node>, scope: Map<String, Any?>) {
            for (node in list) {
                require(++visits <= 100_000) { "Template expansion budget exceeded" }
                when (node) {
                    is Node.Text -> append(node.value)
                    is Node.Field -> {
                        val value = scope[node.key]
                        if (value == null) { unresolved.add(node.key); append("«MISSING:${node.key}»") }
                        else append(value.toString())
                    }
                    is Node.Block -> if (node.each) {
                        val value = scope[node.key]
                        if (value == null) unresolved.add(node.key)
                        else {
                            require(value is List<*>) { "Each field must be a list: ${node.key}" }
                            require(value.size <= 10_000) { "Too many each records" }
                            for (item in value) {
                                require(item is Map<*, *> && item.keys.all { it is String }) { "Each item must be a string-keyed record" }
                                val child = scope.toMutableMap()
                                for ((key, v) in item) child[key as String] = v
                                emit(node.yes, child)
                            }
                        }
                    } else emit(if (truthy(scope[node.key])) node.yes else node.no, scope)
                }
            }
        }
        emit(nodes, row)
        return MergeDocumentResult(index, out.toString(), unresolved)
    }

    fun mergeBatch(template: String, records: List<Map<String, Any?>>): List<MergeDocumentResult> {
        require(records.size in 1..10_000) { "Mail merge requires 1..10000 records" }
        val nodes = parse(template)
        var total = 0L
        return records.mapIndexed { index, row ->
            val result = render(index, nodes, row)
            total += result.renderedBody.length
            require(total <= 16L * MAX_CHARS) { "Batch output budget exceeded" }
            result
        }
    }

    fun mergeSingle(recordIndex: Int, template: String, row: Map<String, Any?>): MergeDocumentResult =
        render(recordIndex, parse(template), row)
}
