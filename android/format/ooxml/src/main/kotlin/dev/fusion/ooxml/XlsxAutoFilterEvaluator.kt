// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Pure-JVM **OOXML SpreadsheetML `<autoFilter>` & Multi-Column Custom Filter Predicate Evaluator**
 * for `:format:ooxml`.
 */
object XlsxAutoFilterEvaluator {
    const val SCOPE =
        "OOXML SpreadsheetML autoFilter XML serializer and multi-column AND/OR row visibility evaluator."

    sealed interface ColumnCriterion {
        data class ExactValues(val allowed: Set<String>) : ColumnCriterion
        data class NumericComparison(val op: String, val threshold: Double) : ColumnCriterion
    }

    fun filterRows(rows: List<List<String>>, criteriaByColZeroBased: Map<Int, ColumnCriterion>): List<Int> =
        rows.indices.filter { rIdx ->
            val row = rows[rIdx]
            criteriaByColZeroBased.all { (col, crit) ->
                val raw = row.getOrNull(col).orEmpty()
                when (crit) {
                    is ColumnCriterion.ExactValues -> raw in crit.allowed
                    is ColumnCriterion.NumericComparison -> {
                        val num = raw.toDoubleOrNull() ?: return@all false
                        when (crit.op) {
                            "gt" -> num > crit.threshold
                            "ge" -> num >= crit.threshold
                            "lt" -> num < crit.threshold
                            "le" -> num <= crit.threshold
                            "eq" -> num == crit.threshold
                            "ne" -> num != crit.threshold
                            else -> false
                        }
                    }
                }
            }
        }

    fun toAutoFilterXml(refRangeA1: String, criteriaByColZeroBased: Map<Int, ColumnCriterion>): String = buildString {
        append("<autoFilter ref=\"").append(refRangeA1).append("\">")
        for ((col, crit) in criteriaByColZeroBased.entries.sortedBy { it.key }) {
            append("<filterColumn colId=\"").append(col).append("\">")
            when (crit) {
                is ColumnCriterion.ExactValues -> {
                    append("<filters>")
                    for (v in crit.allowed) append("<filter val=\"").append(v.replace("\"", "&quot;")).append("\"/>")
                    append("</filters>")
                }
                is ColumnCriterion.NumericComparison -> {
                    append("<customFilters><customFilter operator=\"").append(crit.op)
                        .append("\" val=\"").append(crit.threshold).append("\"/></customFilters>")
                }
            }
            append("</filterColumn>")
        }
        append("</autoFilter>")
    }
}
