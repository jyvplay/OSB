// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/**
 * R146 — bounded PDF annotation planning kernel (owner:v2.0 §FF-03 / PR-08
 * "PDF/image viewer annotations/signing" — the annotation-geometry half).
 *
 * Produces a canonical, clamped annotation plan for the three review shapes
 * the viewer supports (sticky note, highlight, ink) without writing a PDF.
 * Every coordinate is checked against the page MediaBox; a rectangle that
 * falls entirely outside the page is refused instead of silently clamped to
 * a degenerate sliver, and ink polylines must lie wholly inside the page.
 * The plan is the input a later incremental-save writer would consume.
 *
 * Units are PDF user-space points. Floats are printed with exactly four
 * decimal places (half-up on the exact binary value, locale ROOT) so the
 * host JVM and an independent Python oracle can diff byte-for-byte.
 *
 * Declared narrowings: page box coordinates in [-1e6, 1e6] with
 * x0 < x1 and y0 < y1; at most [MAX_QUADS] highlight rectangles and
 * [MAX_INK_POINTS] ink points; opacity in (0, 1]; author <= 255 chars;
 * contents <= 32767 chars (PDF string limit); flags only bits
 * [FLAG_INVISIBLE, FLAG_PRINT, FLAG_NO_VIEW].
 */
object PdfAnnotationPlan {
    const val SCOPE =
        "Clamped TEXT/HIGHLIGHT/INK annotation geometry plans with MediaBox containment refusal; no PDF serialization, no signing."

    const val MAX_QUADS = 512
    const val MAX_INK_POINTS = 4096
    const val MAX_COORD = 1_000_000.0
    const val MAX_AUTHOR = 255
    const val MAX_CONTENTS = 32_607 // 32767 minus escape headroom for the probe codec

    const val FLAG_INVISIBLE = 1
    const val FLAG_PRINT = 4
    const val FLAG_NO_VIEW = 32
    private const val FLAG_MASK = FLAG_INVISIBLE or FLAG_PRINT or FLAG_NO_VIEW

    data class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

    private fun checkCoord(v: Double): Double {
        require(v.isFinite() && v in -MAX_COORD..MAX_COORD) { "coordinate out of range" }
        return v
    }

    fun parseBox(spec: String): Box {
        val p = spec.split(',').map {
            it.toDoubleOrNull() ?: throw IllegalArgumentException("box coordinate not a number")
        }
        require(p.size == 4) { "box needs x0,y0,x1,y1" }
        val b = Box(checkCoord(p[0]), checkCoord(p[1]), checkCoord(p[2]), checkCoord(p[3]))
        require(b.x0 < b.x1 && b.y0 < b.y1) { "empty or inverted MediaBox" }
        return b
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.ROOT, "%.4f", v)

    private fun inside(box: Box, x: Double, y: Double): Boolean =
        x >= box.x0 && x <= box.x1 && y >= box.y0 && y <= box.y1

    private fun clamp(v: Double, lo: Double, hi: Double): Double =
        if (v < lo) lo else if (v > hi) hi else v

    private fun intersect(a: Box, b: Box): Box? {
        val x0 = maxOf(a.x0, b.x0)
        val y0 = maxOf(a.y0, b.y0)
        val x1 = minOf(a.x1, b.x1)
        val y1 = minOf(a.y1, b.y1)
        return if (x0 < x1 && y0 < y1) Box(x0, y0, x1, y1) else null
    }

    private fun checkCommon(page: Box, opacityRaw: String, flagsRaw: String, author: String, contents: String): Pair<Double, Int> {
        val opacity = opacityRaw.toDoubleOrNull() ?: throw IllegalArgumentException("opacity not a number")
        require(opacity > 0.0 && opacity <= 1.0) { "opacity must be in (0, 1]" }
        val flags = flagsRaw.toIntOrNull() ?: throw IllegalArgumentException("flags not an int")
        require(flags in 0..FLAG_MASK && (flags and FLAG_MASK.inv()) == 0) { "unknown annotation flag bit" }
        require(author.length <= MAX_AUTHOR) { "author too long" }
        require(contents.length <= MAX_CONTENTS) { "contents too long" }
        return opacity to flags
    }

    /**
     * Typed plan entry. `author` and `contents` are arbitrary caller text
     * (they may contain any structural character — the caller, not this
     * function, owns their transport encoding).
     * geometry: TEXT = `x,y`; HIGHLIGHT = `x0,y0,x1,y1` repeated with `;`;
     * INK = `x,y` points repeated with `;`.
     *
     * Output: `type|rect=…|quad=…|opacity=…|flags=N|author=…|contents=…`
     * where quad is `q(x0,y0,x1,y1;x2,y2,x3,y3)` per rectangle or the ink
     * polyline `p(x,y;…)`; absent fields render as `-`.
     */
    fun plan(
        pageSpec: String,
        type: String,
        opacityRaw: String,
        flagsRaw: String,
        author: String,
        contents: String,
        geometry: String,
    ): String {
        val page = parseBox(pageSpec)
        val (opacity, flags) = checkCommon(page, opacityRaw, flagsRaw, author, contents)
        return when (type) {
            "TEXT" -> {
                val p = geometry.split(',')
                require(p.size == 2) { "TEXT geometry is x,y" }
                val x = checkCoord(p[0].toDoubleOrNull() ?: throw IllegalArgumentException("x not a number"))
                val y = checkCoord(p[1].toDoubleOrNull() ?: throw IllegalArgumentException("y not a number"))
                require(inside(page, x, y)) { "note anchor outside the page" }
                val rect = Box(x, y, minOf(x + 20.0, page.x1), minOf(y + 20.0, page.y1))
                val clamped = intersect(page, rect) ?: throw IllegalArgumentException("note rect leaves the page")
                "TEXT|rect=${boxStr(clamped)}|quad=-|opacity=${fmt(opacity)}|flags=$flags|author=${author}|contents=${contents}"
            }
            "HIGHLIGHT" -> {
                val rects = if (geometry.isEmpty()) emptyList() else geometry.split(';')
                require(rects.size in 1..MAX_QUADS) { "highlight needs 1..$MAX_QUADS rectangles" }
                val quads = rects.map { r ->
                    val c = r.split(',').map {
                        it.toDoubleOrNull() ?: throw IllegalArgumentException("quad coordinate not a number")
                    }
                    require(c.size == 4) { "each rectangle is x0,y0,x1,y1" }
                    val raw = Box(checkCoord(c[0]), checkCoord(c[1]), checkCoord(c[2]), checkCoord(c[3]))
                    require(raw.x0 < raw.x1 && raw.y0 < raw.y1) { "empty highlight rectangle" }
                    val clamped = intersect(page, raw)
                        ?: throw IllegalArgumentException("highlight rectangle misses the page")
                    // QuadPoints order: bottom-left, bottom-right, top-right, top-left.
                    "q(${fmt(clamped.x0)},${fmt(clamped.y0)},${fmt(clamped.x1)},${fmt(clamped.y0)}," +
                        "${fmt(clamped.x1)},${fmt(clamped.y1)},${fmt(clamped.x0)},${fmt(clamped.y1)})"
                }
                val union = quadsBound(page, rects)
                "HIGHLIGHT|rect=${boxStr(union)}|quad=${quads.joinToString(";")}|opacity=${fmt(opacity)}|flags=$flags|author=${author}|contents=${contents}"
            }
            "INK" -> {
                val pts = if (geometry.isEmpty()) emptyList() else geometry.split(';')
                require(pts.size in 2..MAX_INK_POINTS) { "ink needs 2..$MAX_INK_POINTS points" }
                var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
                var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
                val rendered = pts.map { p ->
                    val c = p.split(',')
                    require(c.size == 2) { "each ink point is x,y" }
                    val x = checkCoord(c[0].toDoubleOrNull() ?: throw IllegalArgumentException("x not a number"))
                    val y = checkCoord(c[1].toDoubleOrNull() ?: throw IllegalArgumentException("y not a number"))
                    require(inside(page, x, y)) { "ink point outside the page" }
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                    "${fmt(x)},${fmt(y)}"
                }
                val rect = Box(minX, minY, maxX, maxY)
                "INK|rect=${boxStr(rect)}|quad=p(${rendered.joinToString(";")})|opacity=${fmt(opacity)}|flags=$flags|author=${author}|contents=${contents}"
            }
            else -> throw IllegalArgumentException("unknown annotation type: $type")
        }
    }

    private fun boxStr(b: Box): String = "${fmt(b.x0)},${fmt(b.y0)},${fmt(b.x1)},${fmt(b.y1)}"

    private fun quadsBound(page: Box, rects: List<String>): Box {
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE
        var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        for (r in rects) {
            val c = r.split(',').map {
                it.toDoubleOrNull() ?: throw IllegalArgumentException("quad coordinate not a number")
            }
            require(c.size == 4) { "each rectangle is x0,y0,x1,y1" }
            val raw = Box(checkCoord(c[0]), checkCoord(c[1]), checkCoord(c[2]), checkCoord(c[3]))
            val clamped = intersect(page, raw) ?: throw IllegalArgumentException("highlight rectangle misses the page")
            if (clamped.x0 < x0) x0 = clamped.x0
            if (clamped.y0 < y0) y0 = clamped.y0
            if (clamped.x1 > x1) x1 = clamped.x1
            if (clamped.y1 > y1) y1 = clamped.y1
        }
        require(x0 <= x1 && y0 <= y1) { "no highlight rectangles" }
        return Box(x0, y0, x1, y1)
    }

    /** Unused in the plan line but kept for callers that only clamp. */
    fun clampPoint(page: Box, x: Double, y: Double): Pair<Double, Double> {
        val cx = clamp(checkCoord(x), page.x0, page.x1)
        val cy = clamp(checkCoord(y), page.y0, page.y1)
        return cx to cy
    }
}
