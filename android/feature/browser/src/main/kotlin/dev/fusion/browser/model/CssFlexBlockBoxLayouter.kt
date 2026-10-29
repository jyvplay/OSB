package dev.fusion.browser.model

/**
 * Clean-room CSS Block + Flexbox 2D Box Layout Engine (inspired by Taffy standalone embeddable
 * CSS layout architecture; Apache-2.0).
 *
 * Solves `display: block` and `display: flex` (`flex-direction: row | column`, `flex-grow`,
 * `flex-shrink`, `flex-basis`, `justify-content: flex-start | center | flex-end | space-between`,
 * `align-items: flex-start | center | flex-end | stretch`, `gap`, `padding`, `border`, `margin`,
 * `minWidth`/`maxWidth`/`minHeight`/`maxHeight`) into exact `(x, y, width, height)` rectangles.
 */
object CssFlexBlockBoxLayouter {
    const val SCOPE =
        "Taffy-inspired CSS Block + Flexbox 2D box-tree layout engine (row/column, grow/shrink/basis, justify/align, gap, box-sizing)"

    enum class DisplayMode { BLOCK, FLEX }
    enum class FlexDirection { ROW, COLUMN }
    enum class JustifyContent { FLEX_START, CENTER, FLEX_END, SPACE_BETWEEN }
    enum class AlignItems { FLEX_START, CENTER, FLEX_END, STRETCH }

    data class EdgeInsets(val left: Double = 0.0, val top: Double = 0.0, val right: Double = 0.0, val bottom: Double = 0.0) {
        val horizontal: Double get() = left + right
        val vertical: Double get() = top + bottom
        companion object {
            fun all(v: Double) = EdgeInsets(v, v, v, v)
        }
    }

    data class BoxStyle(
        val display: DisplayMode = DisplayMode.BLOCK,
        val flexDirection: FlexDirection = FlexDirection.ROW,
        val justifyContent: JustifyContent = JustifyContent.FLEX_START,
        val alignItems: AlignItems = AlignItems.STRETCH,
        val gapPx: Double = 0.0,
        val widthPx: Double? = null,
        val heightPx: Double? = null,
        val minWidthPx: Double = 0.0,
        val maxWidthPx: Double = Double.POSITIVE_INFINITY,
        val minHeightPx: Double = 0.0,
        val maxHeightPx: Double = Double.POSITIVE_INFINITY,
        val flexGrow: Double = 0.0,
        val flexShrink: Double = 1.0,
        val flexBasisPx: Double? = null,
        val padding: EdgeInsets = EdgeInsets(),
        val border: EdgeInsets = EdgeInsets(),
        val margin: EdgeInsets = EdgeInsets(),
    )

    data class DomBoxNode(
        val id: String,
        val style: BoxStyle = BoxStyle(),
        val intrinsicWidthPx: Double = 0.0,
        val intrinsicHeightPx: Double = 0.0,
        val children: List<DomBoxNode> = emptyList(),
    )

    data class ComputedRect(
        val id: String,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val children: List<ComputedRect>,
    ) {
        fun flatten(): Map<String, ComputedRect> {
            val out = LinkedHashMap<String, ComputedRect>()
            fun walk(r: ComputedRect) {
                out[r.id] = r
                for (c in r.children) walk(c)
            }
            walk(this)
            return out
        }
    }

    fun computeLayout(root: DomBoxNode, viewportWidthPx: Double, viewportHeightPx: Double): ComputedRect {
        require(viewportWidthPx > 0.0 && viewportHeightPx > 0.0) { "Viewport must be positive" }
        val w = (root.style.widthPx ?: (viewportWidthPx - root.style.margin.horizontal))
            .coerceIn(root.style.minWidthPx, root.style.maxWidthPx)
        val h = root.style.heightPx?.coerceIn(root.style.minHeightPx, root.style.maxHeightPx)
        return layoutSubtree(root, root.style.margin.left, root.style.margin.top, w, h)
    }

    private fun layoutSubtree(
        node: DomBoxNode,
        absX: Double,
        absY: Double,
        assignedW: Double,
        assignedH: Double?,
    ): ComputedRect {
        val st = node.style
        val insetLeft = st.padding.left + st.border.left
        val insetTop = st.padding.top + st.border.top
        val insetH = st.padding.horizontal + st.border.horizontal
        val insetV = st.padding.vertical + st.border.vertical
        val innerW = (assignedW - insetH).coerceAtLeast(0.0)

        if (node.children.isEmpty()) {
            val finalH = (assignedH ?: (node.intrinsicHeightPx + insetV)).coerceIn(st.minHeightPx, st.maxHeightPx)
            return ComputedRect(node.id, absX, absY, assignedW, finalH, emptyList())
        }

        if (st.display == DisplayMode.BLOCK) {
            var cursorY = absY + insetTop
            val childRects = ArrayList<ComputedRect>(node.children.size)
            for ((idx, ch) in node.children.withIndex()) {
                if (idx > 0) cursorY += st.gapPx
                cursorY += ch.style.margin.top
                val cw = (ch.style.widthPx ?: (innerW - ch.style.margin.horizontal))
                    .coerceIn(ch.style.minWidthPx, ch.style.maxWidthPx)
                val chH = ch.style.heightPx?.coerceIn(ch.style.minHeightPx, ch.style.maxHeightPx)
                val r = layoutSubtree(ch, absX + insetLeft + ch.style.margin.left, cursorY, cw, chH)
                childRects.add(r)
                cursorY += r.height + ch.style.margin.bottom
            }
            val contentBottom = cursorY - (absY + insetTop)
            val finalH = (assignedH ?: (contentBottom + insetV)).coerceIn(st.minHeightPx, st.maxHeightPx)
            return ComputedRect(node.id, absX, absY, assignedW, finalH, childRects)
        }

        // FLEX layout (ROW or COLUMN)
        val isRow = st.flexDirection == FlexDirection.ROW
        val n = node.children.size
        val totalGaps = if (n > 1) st.gapPx * (n - 1) else 0.0
        val bases = DoubleArray(n) { i ->
            val c = node.children[i]
            val cs = c.style
            val raw = cs.flexBasisPx
                ?: (if (isRow) cs.widthPx ?: (c.intrinsicWidthPx + cs.padding.horizontal + cs.border.horizontal)
                else cs.heightPx ?: (c.intrinsicHeightPx + cs.padding.vertical + cs.border.vertical))
            if (isRow) raw.coerceIn(cs.minWidthPx, cs.maxWidthPx) else raw.coerceIn(cs.minHeightPx, cs.maxHeightPx)
        }
        val mainMargins = DoubleArray(n) { i ->
            if (isRow) node.children[i].style.margin.horizontal else node.children[i].style.margin.vertical
        }
        val containerMain = if (isRow) innerW else ((assignedH ?: (bases.sum() + mainMargins.sum() + totalGaps + insetV)) - insetV).coerceAtLeast(0.0)
        val freeSpace = containerMain - (bases.sum() + mainMargins.sum() + totalGaps)

        val mainSizes = DoubleArray(n)
        if (freeSpace > 1e-9) {
            val totalGrow = node.children.sumOf { it.style.flexGrow.coerceAtLeast(0.0) }
            for (i in 0 until n) {
                val g = node.children[i].style.flexGrow.coerceAtLeast(0.0)
                val grown = if (totalGrow > 0.0) bases[i] + freeSpace * (g / totalGrow) else bases[i]
                mainSizes[i] = clampMain(node.children[i].style, isRow, grown)
            }
        } else if (freeSpace < -1e-9) {
            val weightedShrink = node.children.indices.sumOf { node.children[it].style.flexShrink.coerceAtLeast(0.0) * bases[it].coerceAtLeast(1.0) }
            for (i in 0 until n) {
                val wShrink = node.children[i].style.flexShrink.coerceAtLeast(0.0) * bases[i].coerceAtLeast(1.0)
                val shrunk = if (weightedShrink > 0.0) bases[i] + freeSpace * (wShrink / weightedShrink) else bases[i]
                mainSizes[i] = clampMain(node.children[i].style, isRow, shrunk.coerceAtLeast(0.0))
            }
        } else {
            for (i in 0 until n) mainSizes[i] = bases[i]
        }

        // First measure cross sizes
        val naturalCross = DoubleArray(n) { i ->
            val c = node.children[i]
            val cs = c.style
            if (isRow) {
                (cs.heightPx ?: (c.intrinsicHeightPx + cs.padding.vertical + cs.border.vertical)).coerceIn(cs.minHeightPx, cs.maxHeightPx)
            } else {
                (cs.widthPx ?: (c.intrinsicWidthPx + cs.padding.horizontal + cs.border.horizontal)).coerceIn(cs.minWidthPx, cs.maxWidthPx)
            }
        }
        val containerCross = if (isRow) {
            if (assignedH != null) (assignedH - insetV).coerceAtLeast(0.0)
            else (0 until n).maxOfOrNull { naturalCross[it] + node.children[it].style.margin.vertical } ?: 0.0
        } else {
            innerW
        }

        val usedMain = mainSizes.sum() + mainMargins.sum() + totalGaps
        val leftover = (containerMain - usedMain).coerceAtLeast(0.0)
        var mainCursor = when (st.justifyContent) {
            JustifyContent.FLEX_START, JustifyContent.SPACE_BETWEEN -> 0.0
            JustifyContent.CENTER -> leftover * 0.5
            JustifyContent.FLEX_END -> leftover
        }
        val extraGap = if (st.justifyContent == JustifyContent.SPACE_BETWEEN && n > 1) leftover / (n - 1) else 0.0

        val childRects = ArrayList<ComputedRect>(n)
        for (i in 0 until n) {
            val c = node.children[i]
            val cs = c.style
            val crossMargin = if (isRow) cs.margin.vertical else cs.margin.horizontal
            val explicitCross = if (isRow) cs.heightPx else cs.widthPx
            val crossSize = if (st.alignItems == AlignItems.STRETCH && explicitCross == null) {
                (containerCross - crossMargin).coerceAtLeast(0.0)
            } else {
                naturalCross[i]
            }
            val crossFree = (containerCross - crossSize - crossMargin).coerceAtLeast(0.0)
            val crossOffset = when (st.alignItems) {
                AlignItems.FLEX_START, AlignItems.STRETCH -> 0.0
                AlignItems.CENTER -> crossFree * 0.5
                AlignItems.FLEX_END -> crossFree
            }
            if (isRow) {
                mainCursor += cs.margin.left
                val cx = absX + insetLeft + mainCursor
                val cy = absY + insetTop + cs.margin.top + crossOffset
                childRects.add(layoutSubtree(c, cx, cy, mainSizes[i], crossSize))
                mainCursor += mainSizes[i] + cs.margin.right + st.gapPx + extraGap
            } else {
                mainCursor += cs.margin.top
                val cx = absX + insetLeft + cs.margin.left + crossOffset
                val cy = absY + insetTop + mainCursor
                childRects.add(layoutSubtree(c, cx, cy, crossSize, mainSizes[i]))
                mainCursor += mainSizes[i] + cs.margin.bottom + st.gapPx + extraGap
            }
        }

        val finalH = (assignedH ?: ((if (isRow) containerCross else usedMain) + insetV))
            .coerceIn(st.minHeightPx, st.maxHeightPx)
        return ComputedRect(node.id, absX, absY, assignedW, finalH, childRects)
    }

    private fun clampMain(s: BoxStyle, isRow: Boolean, v: Double): Double =
        if (isRow) v.coerceIn(s.minWidthPx, s.maxWidthPx) else v.coerceIn(s.minHeightPx, s.maxHeightPx)
}
