package dev.fusion.format.pdf

/**
 * PDF page-box resolution (PDF 32000-1:2008 §7.9.5 rectangles, §14.11.2 /
 * Table 30 page boxes).
 *
 * Rules implemented: a rectangle is four numbers that may name any two
 * diagonally opposite corners and must be normalized to lower-left /
 * upper-right; CropBox is optional and DEFAULTS to MediaBox; the effective
 * visible region is the intersection of the normalized CropBox with the
 * normalized MediaBox.
 *
 * Declared narrowings: BleedBox/TrimBox/ArtBox follow the same default rule
 * but are not modeled here; UserUnit scaling and Rotate are out of scope. A
 * crop that does not intersect the media at all is refused loudly (viewers
 * silently substitute the media box; this library refuses instead of
 * guessing — the caller decides the fallback).
 */
object CropBox {
    const val SCOPE = "MediaBox/CropBox normalization + intersection only; no UserUnit, Rotate, or other boxes."

    data class Rect(val llx: Double, val lly: Double, val urx: Double, val ury: Double) {
        val width: Double get() = urx - llx
        val height: Double get() = ury - lly
        val area: Double get() = width * height
    }

    fun normalize(a: Double, b: Double, c: Double, d: Double): Rect {
        val r = Rect(minOf(a, c), minOf(b, d), maxOf(a, c), maxOf(b, d))
        require(r.width > 0.0 && r.height > 0.0) { "degenerate rectangle [$a $b $c $d]" }
        return r
    }

    fun intersect(p: Rect, q: Rect): Rect {
        val r = Rect(maxOf(p.llx, q.llx), maxOf(p.lly, q.lly), minOf(p.urx, q.urx), minOf(p.ury, q.ury))
        require(r.width > 0.0 && r.height > 0.0) { "boxes do not intersect" }
        return r
    }

    /** Effective crop per Table 30: absent crop defaults to media; present crop is clipped to media. */
    fun effective(media: Rect, crop: Rect?): Rect = if (crop == null) media else intersect(crop, media)

    /** US Letter and ISO A4 media boxes in default user-space points. */
    val LETTER = Rect(0.0, 0.0, 612.0, 792.0)
    val A4 = Rect(0.0, 0.0, 595.276, 841.89)
}
