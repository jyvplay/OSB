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

    fun mergeBatch(template: String, records: List<Map<String, Any?>>): List<MergeDocumentResult> {
        require(records.isNotEmpty()) { "At least one data record required for mail merge" }
        return records.mapIndexed { idx, row -> mergeSingle(idx, template, row) }
    }

    fun mergeSingle(recordIndex: Int, template: String, row: Map<String, Any?>): MergeDocumentResult {
        val unresolved = LinkedHashSet<String>()
        var text = expandEachBlocks(template, row, unresolved)
        text = expandIfBlocks(text, row)
        val fieldRegex = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")
        text = fieldRegex.replace(text) { m ->
            val key = m.groupValues[1]
            if (key in row && row[key] != null) {
                row[key].toString()
            } else {
                unresolved.add(key)
                "«MISSING:$key»"
            }
        }
        return MergeDocumentResult(recordIndex, text, unresolved)
    }

    private fun expandEachBlocks(tpl: String, row: Map<String, Any?>, unresolved: MutableSet<String>): String {
        val eachRegex = Regex("""\{\{#each\s+([A-Za-z0-9_]+)\s*\}\}([\s\S]*?)\{\{/each\}\}""")
        return eachRegex.replace(tpl) { m ->
            val listKey = m.groupValues[1]
            val body = m.groupValues[2]
            val items = row[listKey] as? List<*> ?: return@replace ""
            items.joinToString("") { item ->
                val subMap = (item as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
                val mergedRow = row + subMap
                val itemFieldRegex = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")
                itemFieldRegex.replace(body) { fm ->
                    val k = fm.groupValues[1]
                    if (k in mergedRow && mergedRow[k] != null) mergedRow[k].toString()
                    else { unresolved.add(k); "«MISSING:$k»" }
                }
            }
        }
    }

    private fun expandIfBlocks(tpl: String, row: Map<String, Any?>): String {
        val ifRegex = Regex("""\{\{#if\s+([A-Za-z0-9_]+)\s*\}\}([\s\S]*?)(?:\{\{#else\}\}([\s\S]*?))?\{\{/if\}\}""")
        var cur = tpl
        while (true) {
            val next = ifRegex.replace(cur) { m ->
                val condKey = m.groupValues[1]
                val thenPart = m.groupValues[2]
                val elsePart = m.groupValues[3]
                val raw = row[condKey]
                val truthy = when (raw) {
                    null -> false
                    is Boolean -> raw
                    is Number -> raw.toDouble() != 0.0
                    is String -> raw.isNotBlank() && !raw.equals("false", ignoreCase = true) && raw != "0"
                    is Collection<*> -> raw.isNotEmpty()
                    else -> true
                }
                if (truthy) thenPart else elsePart
            }
            if (next == cur) break
            cur = next
        }
        return cur
    }
}
