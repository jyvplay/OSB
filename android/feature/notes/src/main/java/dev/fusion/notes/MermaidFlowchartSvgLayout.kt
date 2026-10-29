// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Clean-room Mermaid-subset flowchart parser (`graph TD` / `graph LR`) and Sugiyama layered DAG
 * layout engine (longest-path layer assignment + 2-sweep barycentric edge-crossing minimization)
 * emitting standalone vector `<svg>` diagrams for Notes, Writer, and Slides.
 */
object MermaidFlowchartSvgLayout {
    const val SCOPE =
        "Pure-JVM Mermaid graph TD/LR parser + Sugiyama barycentric layered DAG layout + standalone SVG vector emitter."

    enum class Direction { TD, LR }
    enum class NodeShape { RECT, ROUND, DIAMOND }

    data class NodeLayout(
        val id: String,
        val label: String,
        val shape: NodeShape,
        val layer: Int,
        val orderInLayer: Int,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
    )

    data class EdgeLayout(val from: String, val to: String, val label: String)

    data class DiagramLayout(
        val direction: Direction,
        val nodes: List<NodeLayout>,
        val edges: List<EdgeLayout>,
        val widthPx: Int,
        val heightPx: Int,
        val svg: String,
    )

    private val EDGE_RE = Regex(
        """^([A-Za-z0-9_]+)(?:\[([^\]]+)\]|\(([^)]+)\)|\{([^}]+)\})?\s*-->(?:\|([^|]+)\|)?\s*([A-Za-z0-9_]+)(?:\[([^\]]+)\]|\(([^)]+)\)|\{([^}]+)\})?$""",
    )

    fun render(dsl: String): DiagramLayout {
        val rawLines = dsl.lines().map { it.trim().removeSuffix(";") }.filter { it.isNotEmpty() && !it.startsWith("%%") }
        require(rawLines.isNotEmpty()) { "empty Mermaid flowchart" }
        val header = rawLines.first()
        val dir = when {
            header.startsWith("graph LR", ignoreCase = true) || header.startsWith("flowchart LR", ignoreCase = true) -> Direction.LR
            header.startsWith("graph TD", ignoreCase = true) || header.startsWith("graph TB", ignoreCase = true) ||
                header.startsWith("flowchart TD", ignoreCase = true) -> Direction.TD
            else -> throw IllegalArgumentException("expected 'graph TD' or 'graph LR' header, got '$header'")
        }

        val labels = LinkedHashMap<String, String>()
        val shapes = LinkedHashMap<String, NodeShape>()
        val edges = ArrayList<EdgeLayout>()

        fun regNode(id: String, rectLbl: String?, roundLbl: String?, diaLbl: String?) {
            if (id !in labels) labels[id] = id
            if (id !in shapes) shapes[id] = NodeShape.RECT
            when {
                !rectLbl.isNullOrEmpty() -> { labels[id] = rectLbl; shapes[id] = NodeShape.RECT }
                !roundLbl.isNullOrEmpty() -> { labels[id] = roundLbl; shapes[id] = NodeShape.ROUND }
                !diaLbl.isNullOrEmpty() -> { labels[id] = diaLbl; shapes[id] = NodeShape.DIAMOND }
            }
        }

        for (line in rawLines.drop(1)) {
            val m = EDGE_RE.matchEntire(line)
                ?: throw IllegalArgumentException("unsupported Mermaid edge syntax: '$line'")
            val u = m.groupValues[1]
            val v = m.groupValues[6]
            regNode(u, m.groups[2]?.value, m.groups[3]?.value, m.groups[4]?.value)
            regNode(v, m.groups[7]?.value, m.groups[8]?.value, m.groups[9]?.value)
            edges.add(EdgeLayout(u, v, m.groups[5]?.value?.trim().orEmpty()))
        }
        require(labels.isNotEmpty()) { "diagram contains zero nodes" }

        // Cycle-safe longest-path layer assignment
        val layerOf = LinkedHashMap<String, Int>()
        for (id in labels.keys) layerOf[id] = 0
        repeat(labels.size) {
            var changed = false
            for (e in edges) {
                val uL = layerOf.getValue(e.from)
                val vL = layerOf.getValue(e.to)
                if (e.from != e.to && vL < uL + 1 && uL + 1 < labels.size) {
                    layerOf[e.to] = uL + 1
                    changed = true
                }
            }
            if (!changed) return@repeat
        }

        val maxLayer = layerOf.values.maxOrNull() ?: 0
        val layers = Array(maxLayer + 1) { l -> labels.keys.filter { layerOf[it] == l }.toMutableList() }

        // 2-sweep Sugiyama barycentric crossing reduction
        repeat(2) {
            for (l in 1..maxLayer) {
                val prevPos = layers[l - 1].withIndex().associate { it.value to it.index.toDouble() }
                layers[l].sortBy { node ->
                    val preds = edges.filter { it.to == node && it.from in prevPos }.map { prevPos.getValue(it.from) }
                    if (preds.isEmpty()) 999.0 else preds.average()
                }
            }
        }

        val nodeW = 156.0
        val nodeH = 48.0
        val hGap = 54.0
        val vGap = 62.0
        val pad = 32.0
        val maxBreadth = layers.maxOf { it.size }.coerceAtLeast(1)

        val positioned = LinkedHashMap<String, NodeLayout>()
        for (l in layers.indices) {
            val row = layers[l]
            for ((idx, id) in row.withIndex()) {
                val breadthOffset = (maxBreadth - row.size) * 0.5
                val x = if (dir == Direction.TD) pad + (idx + breadthOffset) * (nodeW + hGap) else pad + l * (nodeW + hGap)
                val y = if (dir == Direction.TD) pad + l * (nodeH + vGap) else pad + (idx + breadthOffset) * (nodeH + vGap)
                positioned[id] = NodeLayout(id, labels.getValue(id), shapes.getValue(id), l, idx, x, y, nodeW, nodeH)
            }
        }

        val svgW = (if (dir == Direction.TD) pad * 2 + maxBreadth * nodeW + (maxBreadth - 1) * hGap else pad * 2 + (maxLayer + 1) * nodeW + maxLayer * hGap).toInt()
        val svgH = (if (dir == Direction.TD) pad * 2 + (maxLayer + 1) * nodeH + maxLayer * vGap else pad * 2 + maxBreadth * nodeH + (maxBreadth - 1) * vGap).toInt()

        val svg = buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" width="$svgW" height="$svgH" viewBox="0 0 $svgW $svgH">""")
            append("""<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#38BDF8"/></marker></defs>""")
            append("""<rect width="$svgW" height="$svgH" fill="#0F172A"/>""")
            for (e in edges) {
                val a = positioned.getValue(e.from)
                val b = positioned.getValue(e.to)
                val (x1, y1, x2, y2) = if (dir == Direction.TD) {
                    listOf(a.x + a.width / 2, a.y + a.height, b.x + b.width / 2, b.y)
                } else {
                    listOf(a.x + a.width, a.y + a.height / 2, b.x, b.y + b.height / 2)
                }
                append("""<line x1="${"%.1f".format(x1)}" y1="${"%.1f".format(y1)}" x2="${"%.1f".format(x2)}" y2="${"%.1f".format(y2)}" stroke="#38BDF8" stroke-width="2" marker-end="url(#arrow)"/>""")
                if (e.label.isNotEmpty()) {
                    val mx = (x1 + x2) / 2.0
                    val my = (y1 + y2) / 2.0 - 4.0
                    append("""<text x="${"%.1f".format(mx)}" y="${"%.1f".format(my)}" fill="#E2E8F0" font-family="sans-serif" font-size="11" text-anchor="middle">${esc(e.label)}</text>""")
                }
            }
            for (n in positioned.values) {
                when (n.shape) {
                    NodeShape.RECT -> append("""<rect x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" rx="4" fill="#1E293B" stroke="#38BDF8" stroke-width="1.5"/>""")
                    NodeShape.ROUND -> append("""<rect x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" rx="20" fill="#1E293B" stroke="#34D399" stroke-width="1.5"/>""")
                    NodeShape.DIAMOND -> {
                        val cx = n.x + n.width / 2; val cy = n.y + n.height / 2
                        append("""<polygon points="$cx,${n.y} ${n.x + n.width},$cy $cx,${n.y + n.height} ${n.x},$cy" fill="#1E293B" stroke="#FBBF24" stroke-width="1.5"/>""")
                    }
                }
                append("""<text x="${n.x + n.width / 2}" y="${n.y + n.height / 2 + 4}" fill="#F8FAFC" font-family="sans-serif" font-size="12" text-anchor="middle">${esc(n.label)}</text>""")
            }
            append("""</svg>""")
        }
        return DiagramLayout(dir, positioned.values.toList(), edges, svgW, svgH, svg)
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
