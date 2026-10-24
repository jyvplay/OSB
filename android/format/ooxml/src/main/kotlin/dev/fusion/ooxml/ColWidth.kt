package dev.fusion.ooxml

/**
 * SpreadsheetML column-width unit conversions (ECMA-376 Part 1 §18.3.1.13).
 *
 * Widths are measured in characters of the normal font's Maximum Digit Width
 * (MDW, in pixels; 7 px for Calibri 11 at 96 dpi). The file's `width`
 * attribute is `Truncate(((chars * MDW + 5) / MDW) * 256) / 256` and the
 * on-screen column occupies `Truncate(((256*chars + Truncate(128/MDW))/256) * MDW)`
 * content pixels plus 5 px of fixed padding (2+2 margin + 1 gridline).
 *
 * Declared narrowings: chars >= 1 only (the sub-one-character ramp uses a
 * different documented curve and is refused here), MDW must be a positive
 * integer, and no font metrics are measured — MDW is an input, never guessed.
 */
object ColWidth {
    const val SCOPE = "ECMA-376 §18.3.1.13 unit conversion only; chars>=1, integer MDW, no font measurement."
    const val PADDING_PX = 5
    const val DEFAULT_MDW = 7          // Calibri 11, 96 dpi
    const val DEFAULT_CHARS = 8.43     // Excel's default column width in characters

    fun contentPx(chars: Double, mdw: Int = DEFAULT_MDW): Int {
        require(mdw > 0) { "MDW $mdw" }
        require(chars >= 1.0 && chars <= 255.0) { "chars $chars outside 1.0..255.0" }
        return (((256.0 * chars + (128 / mdw)) / 256.0) * mdw).toInt()
    }

    fun totalPx(chars: Double, mdw: Int = DEFAULT_MDW): Int = contentPx(chars, mdw) + PADDING_PX

    /** The `width` attribute value serialized into sheet XML. */
    fun fileWidth(chars: Double, mdw: Int = DEFAULT_MDW): Double {
        require(mdw > 0) { "MDW $mdw" }
        require(chars >= 1.0 && chars <= 255.0) { "chars $chars outside 1.0..255.0" }
        return kotlin.math.floor(((chars * mdw + PADDING_PX) / mdw) * 256.0) / 256.0
    }

    /** User-visible character width recovered from a total pixel width. */
    fun charsFromTotalPx(px: Int, mdw: Int = DEFAULT_MDW): Double {
        require(mdw > 0) { "MDW $mdw" }
        require(px > PADDING_PX) { "px $px must exceed padding $PADDING_PX" }
        return kotlin.math.floor((px - PADDING_PX).toDouble() / mdw * 100.0 + 0.5) / 100.0
    }
}
