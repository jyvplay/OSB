package dev.fusion.format.pdf

/**
 * PDF page /Rotate handling (ISO 32000-1 Table 30: "the number of degrees
 * by which the page shall be rotated clockwise ... a multiple of 90",
 * inheritable from the page-tree ancestors, default 0).
 *
 * Declared narrowings: the integer attribute only — no content-stream
 * transformation, no viewer UI rotation stacking beyond modular
 * composition. Non-multiples of 90 are refused (the spec says "shall").
 */
object PdfRotate {
    const val SCOPE = "Rotate normalization, inheritance and visible-size swap only; no content transformation."

    /** Canonical 0/90/180/270 from any multiple of 90 (negatives allowed). */
    fun normalize(rotate: Int): Int {
        require(rotate % 90 == 0) { "/Rotate $rotate is not a multiple of 90 (spec: shall be)" }
        return Math.floorMod(rotate, 360)
    }

    /** Table 30 inheritance: the page's own value, else the nearest ancestor's, else 0. */
    fun resolve(pageToRoot: List<Int?>): Int {
        for (v in pageToRoot) if (v != null) return normalize(v)
        return 0
    }

    /** Width/height of the rotated page as displayed. */
    fun visibleSize(width: Double, height: Double, rotate: Int): Pair<Double, Double> {
        require(width > 0.0 && height > 0.0) { "degenerate page ${width}x$height" }
        return when (normalize(rotate)) {
            90, 270 -> height to width
            else -> width to height
        }
    }

    fun compose(a: Int, b: Int): Int = normalize(normalize(a) + normalize(b))

    fun isPortrait(width: Double, height: Double, rotate: Int): Boolean {
        val (w, h) = visibleSize(width, height, rotate)
        return h >= w
    }
}
