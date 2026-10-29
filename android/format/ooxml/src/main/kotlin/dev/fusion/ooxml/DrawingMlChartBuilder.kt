// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Clean-room ECMA-376 Part 1 §21.2 (`xl/charts/chart1.xml`) DrawingML chart XML serializer,
 * round-trip parser, and deterministic standalone SVG plot generator.
 *
 * Supports `BAR` (`<c:barChart>`), `LINE` (`<c:lineChart>`), `PIE` (`<c:pieChart>`), and
 * `SCATTER` (`<c:scatterChart>`) with multiple named data series and category labels.
 */
object DrawingMlChartBuilder {
    enum class ChartKind(val ooxmlTag: String) {
        BAR("c:barChart"),
        LINE("c:lineChart"),
        PIE("c:pieChart"),
        SCATTER("c:scatterChart"),
    }

    data class ChartSeries(
        val name: String,
        val categories: List<String>,
        val values: List<Double>,
        val colorHex: String = "2563EB",
    ) {
        init {
            require(name.isNotBlank()) { "series name must be non-blank" }
            require(values.isNotEmpty() && values.all { it.isFinite() }) { "series values must be non-empty & finite" }
            require(categories.size == values.size) { "categories (${categories.size}) != values (${values.size})" }
            require(colorHex.matches(Regex("[0-9A-Fa-f]{6}"))) { "colorHex must be 6 hex digits" }
        }
    }

    data class ChartSpec(
        val title: String,
        val kind: ChartKind,
        val series: List<ChartSeries>,
    ) {
        init {
            require(series.isNotEmpty()) { "chart requires at least 1 series" }
        }
    }

    fun buildChartXml(spec: ChartSpec): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""")
        append("""<c:chart><c:title><c:tx><c:rich><a:p><a:r><a:t>""")
        append(escapeXml(spec.title))
        append("""</a:t></a:r></a:p></c:rich></c:tx></c:title><c:plotArea><""")
        append(spec.kind.ooxmlTag).append(">")
        for ((idx, ser) in spec.series.withIndex()) {
            append("""<c:ser><c:idx val="$idx"/><c:order val="$idx"/>""")
            append("""<c:tx><c:v>${escapeXml(ser.name)}</c:v></c:tx>""")
            append("""<c:spPr><a:solidFill><a:srgbClr val="${ser.colorHex.uppercase(Locale.ROOT)}"/></a:solidFill></c:spPr>""")
            append("""<c:cat><c:strLit><c:ptCount val="${ser.categories.size}"/>""")
            for ((pi, cat) in ser.categories.withIndex()) {
                append("""<c:pt idx="$pi"><c:v>${escapeXml(cat)}</c:v></c:pt>""")
            }
            append("""</c:strLit></c:cat>""")
            val valTag = if (spec.kind == ChartKind.SCATTER) "c:yVal" else "c:val"
            append("""<$valTag><c:numLit><c:ptCount val="${ser.values.size}"/>""")
            for ((pi, v) in ser.values.withIndex()) {
                append("""<c:pt idx="$pi"><c:v>${formatNum(v)}</c:v></c:pt>""")
            }
            append("""</c:numLit></$valTag></c:ser>""")
        }
        append("</").append(spec.kind.ooxmlTag).append("></c:plotArea></c:chart></c:chartSpace>")
    }

    fun parseChartXml(xml: String): ChartSpec {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
            "XXE refused in DrawingML chart XML"
        }
        val title = unescapeXml(Regex("""<a:t>(.*?)</a:t>""").find(xml)?.groupValues?.get(1) ?: "Untitled")
        val kind = ChartKind.entries.firstOrNull { xml.contains("<${it.ooxmlTag}>") }
            ?: throw IllegalArgumentException("unsupported or missing DrawingML chart type")
        val series = ArrayList<ChartSeries>()
        for (sm in Regex("""<c:ser>(.*?)</c:ser>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val sXml = sm.groupValues[1]
            val name = unescapeXml(Regex("""<c:tx><c:v>(.*?)</c:v></c:tx>""").find(sXml)?.groupValues?.get(1) ?: "Series")
            val clr = Regex("""<a:srgbClr val="([0-9A-Fa-f]{6})"""").find(sXml)?.groupValues?.get(1) ?: "2563EB"
            val catXml = Regex("""<c:cat>(.*?)</c:cat>""", RegexOption.DOT_MATCHES_ALL).find(sXml)?.groupValues?.get(1) ?: ""
            val cats = Regex("""<c:pt idx="\d+"><c:v>(.*?)</c:v></c:pt>""").findAll(catXml)
                .map { unescapeXml(it.groupValues[1]) }.toList()
            val valXml = Regex("""<c:(?:val|yVal)>(.*?)</c:(?:val|yVal)>""", RegexOption.DOT_MATCHES_ALL)
                .find(sXml)?.groupValues?.get(1) ?: ""
            val vals = Regex("""<c:pt idx="\d+"><c:v>(.*?)</c:v></c:pt>""").findAll(valXml)
                .map { it.groupValues[1].toDouble() }.toList()
            series.add(ChartSeries(name, cats, vals, clr))
        }
        return ChartSpec(title, kind, series)
    }

    fun renderSvg(spec: ChartSpec, widthPx: Int = 480, heightPx: Int = 280): String {
        require(widthPx in 160..2048 && heightPx in 120..2048) { "SVG bounds out of range" }
        val padL = 48.0; val padR = 20.0; val padT = 36.0; val padB = 34.0
        val plotW = widthPx - padL - padR
        val plotH = heightPx - padT - padB
        val allVals = spec.series.flatMap { it.values }
        val minV = minOf(0.0, allVals.minOrNull() ?: 0.0)
        val maxV = maxOf(1e-9, allVals.maxOrNull() ?: 1.0)
        val span = (maxV - minV).coerceAtLeast(1e-9)

        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" width="$widthPx" height="$heightPx" viewBox="0 0 $widthPx $heightPx">""")
            append("""<rect width="$widthPx" height="$heightPx" fill="#0F172A"/>""")
            append("""<text x="${widthPx / 2}" y="22" fill="#F8FAFC" font-size="14" text-anchor="middle">${escapeXml(spec.title)}</text>""")
            if (spec.kind == ChartKind.PIE) {
                val s0 = spec.series.first()
                val total = s0.values.sum().coerceAtLeast(1e-9)
                val cx = widthPx / 2.0; val cy = padT + plotH / 2.0; val r = minOf(plotW, plotH) * 0.42
                val palette = listOf("38BDF8", "34D399", "FBBF24", "F87171", "A78BFA", "FB923C")
                var angle = -Math.PI / 2.0
                for ((i, v) in s0.values.withIndex()) {
                    val sweep = (v.coerceAtLeast(0.0) / total) * 2.0 * Math.PI
                    val x1 = cx + r * cos(angle); val y1 = cy + r * sin(angle)
                    val x2 = cx + r * cos(angle + sweep); val y2 = cy + r * sin(angle + sweep)
                    val large = if (sweep > Math.PI) 1 else 0
                    val clr = palette[i % palette.size]
                    append(String.format(Locale.US, """<path d="M %.2f %.2f L %.2f %.2f A %.2f %.2f 0 %d 1 %.2f %.2f Z" fill="#%s"/>""", cx, cy, x1, y1, r, r, large, x2, y2, clr))
                    angle += sweep
                }
            } else {
                for ((si, ser) in spec.series.withIndex()) {
                    val n = ser.values.size
                    val pts = ser.values.mapIndexed { i, v ->
                        val x = padL + (if (n == 1) plotW / 2.0 else (i.toDouble() / (n - 1)) * plotW)
                        val y = padT + plotH - ((v - minV) / span) * plotH
                        x to y
                    }
                    when (spec.kind) {
                        ChartKind.BAR -> {
                            val groupW = plotW / n.coerceAtLeast(1)
                            val barW = (groupW * 0.72) / spec.series.size
                            val zeroY = padT + plotH - ((0.0 - minV) / span) * plotH
                            for ((i, v) in ser.values.withIndex()) {
                                val x = padL + i * groupW + groupW * 0.14 + si * barW
                                val yVal = padT + plotH - ((v - minV) / span) * plotH
                                val top = minOf(zeroY, yVal)
                                val h = kotlin.math.abs(zeroY - yVal).coerceAtLeast(1.0)
                                append(String.format(Locale.US, """<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" fill="#%s"/>""", x, top, barW, h, ser.colorHex))
                            }
                        }
                        ChartKind.LINE -> {
                            val poly = pts.joinToString(" ") { String.format(Locale.US, "%.2f,%.2f", it.first, it.second) }
                            append("""<polyline fill="none" stroke="#${ser.colorHex}" stroke-width="2" points="$poly"/>""")
                        }
                        ChartKind.SCATTER -> {
                            for ((x, y) in pts) {
                                append(String.format(Locale.US, """<circle cx="%.2f" cy="%.2f" r="3.5" fill="#%s"/>""", x, y, ser.colorHex))
                            }
                        }
                        ChartKind.PIE -> {}
                    }
                }
            }
            append("</svg>")
        }
    }

    private fun formatNum(v: Double): String {
        val asLong = v.toLong()
        return if (v == asLong.toDouble()) asLong.toString() else v.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unescapeXml(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
}
