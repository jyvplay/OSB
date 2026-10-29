// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Pure-JVM OOXML SpreadsheetML `<x14:sparklineGroups>` (ISO/IEC 29500 / MS-XLSX) Sparkline
 * serializer, parser, and normalized coordinate curve generator (`LINE`, `COLUMN`, `WIN_LOSS`).
 */
object OoxmlSparklineEngine {
    const val SCOPE =
        "OOXML SpreadsheetML x14:sparklineGroups (LINE / COLUMN / WIN_LOSS) XML serializer, bounded parser, and high/low/first/last marker coordinate solver."

    enum class SparklineKind(val xmlAttr: String) {
        LINE("line"),
        COLUMN("column"),
        WIN_LOSS("stacked"),
    }

    data class SparklineSeries(
        val sheetName: String,
        val dataRangeA1: String,
        val locationCellA1: String,
        val kind: SparklineKind = SparklineKind.LINE,
        val showMarkers: Boolean = true,
        val highlightHighLow: Boolean = true,
    )

    data class NormalizedSparkPoint(
        val index: Int,
        val normalizedY: Double, // -1.0..+1.0 for WIN_LOSS, 0.0..1.0 for LINE/COLUMN
        val isHigh: Boolean,
        val isLow: Boolean,
    )

    fun toSparklineExtXml(groups: List<SparklineSeries>): String {
        require(groups.isNotEmpty()) { "At least one sparkline series required" }
        val sb = StringBuilder()
        sb.append("<x14:sparklineGroups xmlns:x14=\"http://schemas.microsoft.com/office/spreadsheetml/2009/9/main\" xmlns:xm=\"http://schemas.microsoft.com/office/excel/2006/main\">")
        for (g in groups) {
            val safeSheet = g.sheetName.replace("'", "''")
            sb.append("<x14:sparklineGroup type=\"").append(g.kind.xmlAttr).append("\"")
            if (g.showMarkers) sb.append(" markers=\"1\"")
            if (g.highlightHighLow) sb.append(" high=\"1\" low=\"1\"")
            sb.append("><x14:sparklines><x14:sparkline>")
            sb.append("<xm:f>'").append(escapeXml(safeSheet)).append("'!")
                .append(escapeXml(g.dataRangeA1)).append("</xm:f>")
            sb.append("<xm:sqref>").append(escapeXml(g.locationCellA1)).append("</xm:sqref>")
            sb.append("</x14:sparkline></x14:sparklines></x14:sparklineGroup>")
        }
        sb.append("</x14:sparklineGroups>")
        return sb.toString()
    }

    fun parseSparklineExtXml(xml: String): List<SparklineSeries> {
        val doc = BoundedXml.parse(xml.toByteArray(Charsets.UTF_8))
        val out = ArrayList<SparklineSeries>()
        for (grp in collectLocal(doc, "sparklineGroup")) {
            val typeStr = localAttr(grp, "type") ?: "line"
            val kind = SparklineKind.entries.firstOrNull { it.xmlAttr == typeStr } ?: SparklineKind.LINE
            val markers = localAttr(grp, "markers") == "1"
            val highLow = localAttr(grp, "high") == "1" && localAttr(grp, "low") == "1"
            for (sp in collectLocal(grp, "sparkline")) {
                val fText = sp.children.firstOrNull { it.name.local == "f" }?.text?.trim().orEmpty()
                val sqref = sp.children.firstOrNull { it.name.local == "sqref" }?.text?.trim().orEmpty()
                val bang = fText.lastIndexOf('!')
                val sheet = if (bang > 0) fText.substring(0, bang).trim('\'').replace("''", "'") else "Sheet1"
                val range = if (bang > 0) fText.substring(bang + 1) else fText
                if (range.isNotEmpty() && sqref.isNotEmpty()) {
                    out.add(SparklineSeries(sheet, range, sqref, kind, markers, highLow))
                }
            }
        }
        return out
    }

    private fun collectLocal(node: BoundedXml.Node, local: String): List<BoundedXml.Node> {
        val acc = ArrayList<BoundedXml.Node>()
        fun walk(n: BoundedXml.Node) {
            if (n.name.local == local) acc.add(n)
            for (c in n.children) walk(c)
        }
        walk(node)
        return acc
    }

    private fun localAttr(node: BoundedXml.Node, local: String): String? =
        node.attributes.entries.firstOrNull { it.key.local == local }?.value

    fun computeNormalizedGeometry(values: List<Double>, kind: SparklineKind): List<NormalizedSparkPoint> {
        if (values.isEmpty()) return emptyList()
        val hi = values.max()
        val lo = values.min()
        val span = (hi - lo).let { if (it == 0.0) 1.0 else it }
        return values.mapIndexed { idx, v ->
            val ny = when (kind) {
                SparklineKind.WIN_LOSS -> when {
                    v > 0.0 -> 1.0
                    v < 0.0 -> -1.0
                    else -> 0.0
                }
                SparklineKind.LINE, SparklineKind.COLUMN ->
                    if (hi == lo) 0.5 else (v - lo) / span
            }
            NormalizedSparkPoint(idx, ny, isHigh = v == hi, isLow = v == lo)
        }
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
