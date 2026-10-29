// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Clean-room spreadsheet SVG chart generator (`SvgChartPlot`).
 *
 * Powers `:feature:calc` data visualization (`BAR`, `LINE`, `AREA`, `PIE`) without WebView/Chart.js:
 * - Heckbert-style nice axis tick calculation (`niceTicks`).
 * - Multi-series palette with high-contrast accessible stroke/fill pairs.
 * - Emits standalone, script-free `<svg>` with `<title>`/`<desc>` AX tags and strict XML entity escaping.
 */
object SvgChartPlot {
    const val SCOPE =
        "Pure-JVM spreadsheet SVG chart renderer (BAR, LINE, AREA, PIE, Heckbert nice axis ticks, multi-series legends, AX title/desc); 3D perspective and logarithmic dual-axes out of scope."

    enum class ChartKind { BAR, LINE, AREA, PIE }

    data class Series(
        val name: String,
        val values: List<Double>,
        val colorHex: String? = null,
    ) {
        init {
            require(name.isNotBlank()) { "Series name must not be blank" }
            require(values.isNotEmpty()) { "Series values must not be empty" }
            values.forEach { require(it.isFinite()) { "Series value must be finite: $it" } }
            colorHex?.let { require(HEX_RE.matches(it)) { "Invalid hex color: $it" } }
        }
    }

    data class ChartSpec(
        val kind: ChartKind,
        val title: String,
        val categories: List<String>,
        val series: List<Series>,
        val widthPx: Int = 640,
        val heightPx: Int = 380,
    ) {
        init {
            require(categories.isNotEmpty()) { "categories must not be empty" }
            require(series.isNotEmpty()) { "series must not be empty" }
            series.forEach { require(it.values.size == categories.size) { "Series '${it.name}' length != categories" } }
            require(widthPx in 240..4096 && heightPx in 180..4096) { "Invalid chart dimensions" }
        }
    }

    private val HEX_RE = Regex("^#[0-9A-Fa-f]{6}$")
    private val PALETTE = listOf("#2563EB", "#059669", "#D97706", "#DC2626", "#7C3AED", "#0891B2")

    fun niceTicks(minVal: Double, maxVal: Double, targetCount: Int = 5): List<Double> {
        val lo = min(0.0, minVal)
        val hi = if (maxVal <= lo) lo + 1.0 else maxVal
        val rawStep = (hi - lo) / max(1, targetCount)
        val mag = 10.0.pow(floor(log10(rawStep)))
        val residual = rawStep / mag
        val niceMult = when {
            residual <= 1.0 -> 1.0
            residual <= 2.0 -> 2.0
            residual <= 5.0 -> 5.0
            else -> 10.0
        }
        val step = niceMult * mag
        val start = floor(lo / step) * step
        val end = ceil(hi / step) * step
        val ticks = mutableListOf<Double>()
        var cur = start
        var guard = 0
        while (cur <= end + step * 1e-6 && guard < 32) {
            ticks.add(if (kotlin.math.abs(cur) < 1e-12) 0.0 else cur)
            cur += step
            guard++
        }
        return ticks
    }

    fun render(spec: ChartSpec): String {
        val w = spec.widthPx.toDouble()
        val h = spec.heightPx.toDouble()
        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" width="${spec.widthPx}" height="${spec.heightPx}" viewBox="0 0 ${spec.widthPx} ${spec.heightPx}" role="img">""")
            append("<title>${esc(spec.title)}</title>")
            append("<desc>${esc("${spec.kind} chart with ${spec.series.size} series across ${spec.categories.size} categories")}</desc>")
            append("""<rect x="0" y="0" width="${spec.widthPx}" height="${spec.heightPx}" rx="10" fill="#FFFFFF" stroke="#E5E7EB"/>""")
            append("""<text x="${fmt(w / 2)}" y="28" text-anchor="middle" font-family="sans-serif" font-size="15" font-weight="600" fill="#111827">${esc(spec.title)}</text>""")
            if (spec.kind == ChartKind.PIE) {
                renderPie(spec.categories, spec.series.first(), w, h)
            } else {
                renderCartesian(spec, w, h)
            }
            append("</svg>")
        }
    }

    private fun StringBuilder.renderCartesian(spec: ChartSpec, w: Double, h: Double) {
        val padL = 62.0
        val padR = 24.0
        val padT = 48.0
        val padB = 64.0
        val plotW = w - padL - padR
        val plotH = h - padT - padB
        val allVals = spec.series.flatMap { it.values }
        val ticks = niceTicks(allVals.min(), allVals.max())
        val yMin = ticks.first()
        val yMax = ticks.last().let { if (it <= yMin) yMin + 1.0 else it }

        fun yPx(v: Double): Double = padT + plotH - ((v - yMin) / (yMax - yMin)) * plotH
        val zeroY = yPx(0.0.coerceIn(yMin, yMax))

        for (t in ticks) {
            val y = yPx(t)
            append("""<line x1="${fmt(padL)}" y1="${fmt(y)}" x2="${fmt(padL + plotW)}" y2="${fmt(y)}" stroke="#E5E7EB" stroke-width="1"/>""")
            append("""<text x="${fmt(padL - 8)}" y="${fmt(y + 4)}" text-anchor="end" font-family="sans-serif" font-size="11" fill="#4B5563">${fmt(t)}</text>""")
        }

        val nCat = spec.categories.size
        val groupW = plotW / nCat
        spec.categories.forEachIndexed { idx, cat ->
            val cx = padL + groupW * (idx + 0.5)
            append("""<text x="${fmt(cx)}" y="${fmt(padT + plotH + 20)}" text-anchor="middle" font-family="sans-serif" font-size="11" fill="#374151">${esc(cat)}</text>""")
        }

        spec.series.forEachIndexed { sIdx, s ->
            val color = s.colorHex ?: PALETTE[sIdx % PALETTE.size]
            when (spec.kind) {
                ChartKind.BAR -> {
                    val barW = (groupW * 0.72) / spec.series.size
                    s.values.forEachIndexed { cIdx, v ->
                        val bx = padL + cIdx * groupW + groupW * 0.14 + sIdx * barW
                        val vy = yPx(v)
                        val top = min(vy, zeroY)
                        val bh = max(1.0, kotlin.math.abs(zeroY - vy))
                        append("""<rect x="${fmt(bx)}" y="${fmt(top)}" width="${fmt(max(2.0, barW - 2.0))}" height="${fmt(bh)}" fill="$color" data-series="${esc(s.name)}"/>""")
                    }
                }
                ChartKind.LINE, ChartKind.AREA -> {
                    val pts = s.values.mapIndexed { cIdx, v ->
                        val px = padL + groupW * (cIdx + 0.5)
                        val py = yPx(v)
                        px to py
                    }
                    if (spec.kind == ChartKind.AREA && pts.isNotEmpty()) {
                        val poly = buildString {
                            append("${fmt(pts.first().first)},${fmt(zeroY)} ")
                            pts.forEach { (px, py) -> append("${fmt(px)},${fmt(py)} ") }
                            append("${fmt(pts.last().first)},${fmt(zeroY)}")
                        }
                        append("""<polygon points="$poly" fill="$color" fill-opacity="0.18"/>""")
                    }
                    val linePts = pts.joinToString(" ") { (px, py) -> "${fmt(px)},${fmt(py)}" }
                    append("""<polyline fill="none" stroke="$color" stroke-width="2.5" points="$linePts" data-series="${esc(s.name)}"/>""")
                    pts.forEach { (px, py) ->
                        append("""<circle cx="${fmt(px)}" cy="${fmt(py)}" r="3.5" fill="$color"/>""")
                    }
                }
                ChartKind.PIE -> Unit
            }
            // Legend entry
            val lx = padL + sIdx * 120.0
            val ly = h - 18.0
            append("""<rect x="${fmt(lx)}" y="${fmt(ly - 9)}" width="10" height="10" rx="2" fill="$color"/>""")
            append("""<text x="${fmt(lx + 15)}" y="${fmt(ly)}" font-family="sans-serif" font-size="11" fill="#1F2937">${esc(s.name)}</text>""")
        }
    }

    private fun StringBuilder.renderPie(categories: List<String>, series: Series, w: Double, h: Double) {
        val positives = series.values.map { max(0.0, it) }
        val sum = positives.sum()
        require(sum > 0.0) { "PIE chart requires positive total weight" }
        val cx = w * 0.38
        val cy = h * 0.55
        val r = min(w, h) * 0.30
        var angle = -PI / 2.0
        positives.forEachIndexed { idx, v ->
            val color = PALETTE[idx % PALETTE.size]
            val sweep = (v / sum) * 2.0 * PI
            val end = angle + sweep
            val x1 = cx + r * cos(angle)
            val y1 = cy + r * sin(angle)
            val x2 = cx + r * cos(end)
            val y2 = cy + r * sin(end)
            val largeArc = if (sweep > PI) 1 else 0
            if (sweep >= 2.0 * PI - 1e-6) {
                append("""<circle cx="${fmt(cx)}" cy="${fmt(cy)}" r="${fmt(r)}" fill="$color"/>""")
            } else if (sweep > 1e-6) {
                append("""<path d="M ${fmt(cx)} ${fmt(cy)} L ${fmt(x1)} ${fmt(y1)} A ${fmt(r)} ${fmt(r)} 0 $largeArc 1 ${fmt(x2)} ${fmt(y2)} Z" fill="$color" stroke="#FFFFFF" stroke-width="1.5"/>""")
            }
            val ly = 66.0 + idx * 22.0
            val pct = (v / sum) * 100.0
            append("""<rect x="${fmt(w * 0.68)}" y="${fmt(ly - 9)}" width="10" height="10" rx="2" fill="$color"/>""")
            append("""<text x="${fmt(w * 0.68 + 16)}" y="${fmt(ly)}" font-family="sans-serif" font-size="11" fill="#1F2937">${esc(categories[idx])} (${fmt(pct)}%)</text>""")
            angle = end
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }
}
