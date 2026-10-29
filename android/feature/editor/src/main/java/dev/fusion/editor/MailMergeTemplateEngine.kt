// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

import java.util.Locale

/**
 * Clean-room Writer Mail-Merge & conditional template engine over tabular records.
 *
 * Supports:
 * - Variable substitution `{{field}}` and default fallback `{{field|"fallback"}}`.
 * - Format pipes `{{amount|currency}}`, `{{name|upper}}`, `{{name|lower}}`.
 * - Conditional sections `{{#if vip}}...{{else}}...{{/if}}` (nestable up to depth 8).
 * - Strict missing-field diagnostics without arbitrary code evaluation or reflection.
 */
object MailMergeTemplateEngine {
    const val MAX_TEMPLATE_CHARS = 65_536
    const val MAX_RECORDS = 10_000

    data class MergedDocument(
        val recordIndex: Int,
        val renderedText: String,
        val unresolvedFields: Set<String>,
    )

    data class BatchMergeReport(
        val documents: List<MergedDocument>,
        val totalUnresolvedOccurrences: Int,
    )

    fun mergeBatch(template: String, records: List<Map<String, String>>): BatchMergeReport {
        require(template.length <= MAX_TEMPLATE_CHARS) { "template exceeds $MAX_TEMPLATE_CHARS chars" }
        require(records.size <= MAX_RECORDS) { "records exceed $MAX_RECORDS" }
        var unresolvedTotal = 0
        val docs = records.mapIndexed { idx, row ->
            val missing = LinkedHashSet<String>()
            val text = evalBlocks(template, row, missing, depth = 0)
            unresolvedTotal += missing.size
            MergedDocument(idx, text, missing)
        }
        return BatchMergeReport(docs, unresolvedTotal)
    }

    private fun evalBlocks(
        src: String,
        row: Map<String, String>,
        missing: MutableSet<String>,
        depth: Int,
    ): String {
        require(depth <= 8) { "mail-merge conditional nesting exceeds 8" }
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            val open = src.indexOf("{{", i)
            if (open < 0) {
                out.append(src.substring(i))
                break
            }
            out.append(src.substring(i, open))
            val close = src.indexOf("}}", open + 2)
            require(close >= 0) { "unclosed '{{' at offset $open" }
            val tag = src.substring(open + 2, close).trim()
            if (tag.startsWith("#if ")) {
                val condKey = tag.removePrefix("#if ").trim()
                val (trueBranch, falseBranch, afterEnd) = extractConditionalBranches(src, close + 2)
                val truthy = isTruthy(row[condKey])
                val chosen = if (truthy) trueBranch else (falseBranch ?: "")
                out.append(evalBlocks(chosen, row, missing, depth + 1))
                i = afterEnd
            } else {
                out.append(resolveToken(tag, row, missing))
                i = close + 2
            }
        }
        return out.toString()
    }

    private fun extractConditionalBranches(src: String, bodyStart: Int): Triple<String, String?, Int> {
        var depth = 1
        var cursor = bodyStart
        var elseSplit = -1
        while (cursor < src.length) {
            val nextOpen = src.indexOf("{{", cursor)
            require(nextOpen >= 0) { "unclosed {{#if}} block" }
            val nextClose = src.indexOf("}}", nextOpen + 2)
            require(nextClose >= 0) { "unclosed tag inside {{#if}}" }
            val inner = src.substring(nextOpen + 2, nextClose).trim()
            when {
                inner.startsWith("#if ") -> depth++
                inner == "else" && depth == 1 && elseSplit < 0 -> elseSplit = nextOpen
                inner == "/if" -> {
                    depth--
                    if (depth == 0) {
                        return if (elseSplit >= 0) {
                            val elseClose = src.indexOf("}}", elseSplit + 2) + 2
                            Triple(
                                src.substring(bodyStart, elseSplit),
                                src.substring(elseClose, nextOpen),
                                nextClose + 2,
                            )
                        } else {
                            Triple(src.substring(bodyStart, nextOpen), null, nextClose + 2)
                        }
                    }
                }
            }
            cursor = nextClose + 2
        }
        throw IllegalArgumentException("unterminated {{#if}} block")
    }

    private fun isTruthy(raw: String?): Boolean {
        if (raw == null) return false
        val norm = raw.trim().lowercase(Locale.ROOT)
        return norm.isNotEmpty() && norm !in setOf("false", "0", "no", "null", "none")
    }

    private fun resolveToken(expr: String, row: Map<String, String>, missing: MutableSet<String>): String {
        val parts = expr.split('|').map { it.trim() }
        val key = parts[0]
        var value = row[key]
        var startPipe = 1
        if ((value == null || value.isEmpty()) && parts.size >= 2 &&
            parts[1].startsWith("\"") && parts[1].endsWith("\"") && parts[1].length >= 2
        ) {
            value = parts[1].substring(1, parts[1].length - 1)
            startPipe = 2
        }
        if (value == null) {
            missing.add(key)
            return "«MISSING:$key»"
        }
        var cur: String = value
        for (p in startPipe until parts.size) {
            cur = when (parts[p].lowercase(Locale.ROOT)) {
                "upper" -> cur.uppercase(Locale.ROOT)
                "lower" -> cur.lowercase(Locale.ROOT)
                "trim" -> cur.trim()
                "currency" -> {
                    val d = cur.toDoubleOrNull() ?: throw IllegalArgumentException("non-numeric currency '$cur'")
                    String.format(Locale.US, "$%,.2f", d)
                }
                else -> throw IllegalArgumentException("unsupported mail-merge pipe '${parts[p]}'")
            }
        }
        return cur
    }
}
