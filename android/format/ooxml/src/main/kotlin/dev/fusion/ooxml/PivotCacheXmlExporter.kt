// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import kotlin.math.max
import kotlin.math.min

/**
 * Clean-room SpreadsheetML PivotCache + PivotTable 2D OLAP aggregation & XML exporter
 * (`ECMA-376 Part 1 §18.10`).
 */
object PivotCacheXmlExporter {
    const val SCOPE =
        "Pure-JVM ECMA-376 SpreadsheetML PivotCache & PivotTable 2D row/column/value aggregator (SUM, COUNT, AVERAGE, MIN, MAX) with deterministic XML serialization; external OLAP MDX cube connections refused."

    enum class AggOp { SUM, COUNT, AVERAGE, MIN, MAX }

    data class PivotRecord(val fields: Map<String, String>)

    data class PivotCubeResult(
        val rowKeys: List<String>,
        val colKeys: List<String>,
        val matrix: List<List<Double>>,
        val rowTotals: List<Double>,
        val colTotals: List<Double>,
        val grandTotal: Double,
        val cacheDefinitionXml: String,
        val pivotTableXml: String,
    )

    fun buildPivot(
        sheetName: String,
        sourceRange: String,
        records: List<PivotRecord>,
        rowField: String,
        colField: String,
        valueField: String,
        op: AggOp = AggOp.SUM,
    ): PivotCubeResult {
        require(sheetName.isNotBlank() && sourceRange.isNotBlank()) { "sheetName/sourceRange required" }
        require(records.size in 1..16_384) { "records size ${records.size} outside 1..16384" }
        val rowKeys = records.mapNotNull { it.fields[rowField]?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
        val colKeys = records.mapNotNull { it.fields[colField]?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
        require(rowKeys.isNotEmpty() && colKeys.isNotEmpty()) { "empty row or column dimension" }

        val buckets = HashMap<Pair<String, String>, MutableList<Double>>()
        val byRow = HashMap<String, MutableList<Double>>()
        val byCol = HashMap<String, MutableList<Double>>()
        val allVals = ArrayList<Double>()

        for (rec in records) {
            val rk = rec.fields[rowField]?.trim() ?: continue
            val ck = rec.fields[colField]?.trim() ?: continue
            val rawV = rec.fields[valueField]?.trim()?.toDoubleOrNull() ?: continue
            if (!rawV.isFinite()) continue
            buckets.getOrPut(rk to ck) { ArrayList() }.add(rawV)
            byRow.getOrPut(rk) { ArrayList() }.add(rawV)
            byCol.getOrPut(ck) { ArrayList() }.add(rawV)
            allVals.add(rawV)
        }

        fun reduce(vs: List<Double>?): Double {
            if (vs.isNullOrEmpty()) return 0.0
            return when (op) {
                AggOp.SUM -> vs.sum()
                AggOp.COUNT -> vs.size.toDouble()
                AggOp.AVERAGE -> vs.sum() / vs.size
                AggOp.MIN -> vs.fold(Double.POSITIVE_INFINITY) { a, b -> min(a, b) }
                AggOp.MAX -> vs.fold(Double.NEGATIVE_INFINITY) { a, b -> max(a, b) }
            }
        }

        val matrix = rowKeys.map { r -> colKeys.map { c -> reduce(buckets[r to c]) } }
        val rowTotals = rowKeys.map { r -> reduce(byRow[r]) }
        val colTotals = colKeys.map { c -> reduce(byCol[c]) }
        val grandTotal = reduce(allVals)

        val fieldNames = listOf(rowField, colField, valueField).distinct()
        val cacheXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<pivotCacheDefinition xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" recordCount="${records.size}">""")
            append("""<cacheSource type="worksheet"><worksheetSource ref="${esc(sourceRange)}" sheet="${esc(sheetName)}"/></cacheSource>""")
            append("""<cacheFields count="${fieldNames.size}">""")
            for (fn in fieldNames) {
                append("""<cacheField name="${esc(fn)}" numFmtId="0"><sharedItems/></cacheField>""")
            }
            append("""</cacheFields></pivotCacheDefinition>""")
        }
        val subAttr = op.name.lowercase()
        val tableXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<pivotTableDefinition xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" name="FusionPivot1" cacheId="1" dataCaption="Values">""")
            append("""<location ref="A1:${ColLetters.toLetters(colKeys.size + 2)}${rowKeys.size + 2}" firstHeaderRow="1" firstDataRow="2" firstDataCol="1"/>""")
            append("""<rowFields count="1"><field x="0"/></rowFields>""")
            append("""<colFields count="1"><field x="1"/></colFields>""")
            append("""<dataFields count="1"><dataField name="${esc("${op.name} of $valueField")}" fld="2" subtotal="$subAttr"/></dataFields>""")
            append("""</pivotTableDefinition>""")
        }
        return PivotCubeResult(rowKeys, colKeys, matrix, rowTotals, colTotals, grandTotal, cacheXml, tableXml)
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
