// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * ECMA-376 SpreadsheetML PivotCache (`xl/pivotCache/pivotCacheDefinition1.xml` +
 * `pivotCacheRecords1.xml`) shared-items dictionary + indexed record table emitter for Calc
 * PivotTable `.xlsx` export.
 */
object XlsxPivotCacheRecordsWriter {
    const val SCOPE =
        "ECMA-376 SpreadsheetML pivotCacheDefinition + pivotCacheRecords XML emitter with deduplicated <sharedItems> lookup."

    data class PivotCacheBundle(
        val definitionXml: String,
        val recordsXml: String,
        val recordCount: Int,
        val distinctCategoriesPerField: Map<String, Int>,
    )

    fun buildPivotCache(
        sheetName: String,
        refRangeA1: String,
        headers: List<String>,
        rows: List<List<Any?>>,
    ): PivotCacheBundle {
        require(headers.isNotEmpty() && rows.all { it.size == headers.size })
        val stringPools = Array(headers.size) { LinkedHashSet<String>() }
        val numericOnly = BooleanArray(headers.size) { true }

        for (r in rows) {
            for ((c, v) in r.withIndex()) {
                when (v) {
                    is Number -> Unit
                    null -> Unit
                    else -> {
                        numericOnly[c] = false
                        stringPools[c].add(v.toString())
                    }
                }
            }
        }

        val poolLists = stringPools.map { it.toList() }
        val defSb = StringBuilder()
        defSb.append("<pivotCacheDefinition xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" recordCount=\"${rows.size}\">")
        defSb.append("<cacheSource type=\"worksheet\"><worksheetSource ref=\"${esc(refRangeA1)}\" sheet=\"${esc(sheetName)}\"/></cacheSource>")
        defSb.append("<cacheFields count=\"${headers.size}\">")
        val counts = LinkedHashMap<String, Int>()
        for ((c, h) in headers.withIndex()) {
            defSb.append("<cacheField name=\"${esc(h)}\" numFmtId=\"0\">")
            if (numericOnly[c]) {
                defSb.append("<sharedItems containsNumber=\"1\" containsString=\"0\"/>")
                counts[h] = 0
            } else {
                val items = poolLists[c]
                counts[h] = items.size
                defSb.append("<sharedItems count=\"${items.size}\">")
                for (s in items) defSb.append("<s v=\"${esc(s)}\"/>")
                defSb.append("</sharedItems>")
            }
            defSb.append("</cacheField>")
        }
        defSb.append("</cacheFields></pivotCacheDefinition>")

        val recSb = StringBuilder()
        recSb.append("<pivotCacheRecords xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${rows.size}\">")
        for (r in rows) {
            recSb.append("<r>")
            for ((c, v) in r.withIndex()) {
                when {
                    v == null -> recSb.append("<m/>")
                    numericOnly[c] && v is Number -> recSb.append("<n v=\"${v.toDouble()}\"/>")
                    else -> {
                        val idx = poolLists[c].indexOf(v.toString()).coerceAtLeast(0)
                        recSb.append("<x v=\"$idx\"/>")
                    }
                }
            }
            recSb.append("</r>")
        }
        recSb.append("</pivotCacheRecords>")
        return PivotCacheBundle(defSb.toString(), recSb.toString(), rows.size, counts)
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
