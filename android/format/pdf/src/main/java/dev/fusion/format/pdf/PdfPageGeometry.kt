package dev.fusion.format.pdf

/**
 * Composed page geometry: MediaBox + optional CropBox + inherited /Rotate
 * (ISO 32000-1 §7.7.3.3 Table 30), built on [CropBox] and [PdfRotate].
 *
 * The display surface a viewer actually shows is: normalize boxes,
 * default/clip the crop against the media ([CropBox.effective]), then
 * swap width/height for 90/270 rotation ([PdfRotate.visibleSize]).
 *
 * Declared narrowings: /UserUnit other than 1.0 is REFUSED (not scaled) —
 * oversized technical drawings need a real decision, not a silent
 * multiplier; BleedBox/TrimBox/ArtBox remain unmodeled; content-stream
 * transformation is a renderer concern.
 */
object PdfPageGeometry {
    const val SCOPE = "Box+rotation composition only; UserUnit != 1.0 refused; no content transformation."
    const val POINTS_PER_INCH = 72.0

    data class Display(
        val box: CropBox.Rect, val rotation: Int,
        val widthPt: Double, val heightPt: Double
    ) {
        val widthInches: Double get() = widthPt / POINTS_PER_INCH
        val heightInches: Double get() = heightPt / POINTS_PER_INCH
        val portrait: Boolean get() = heightPt >= widthPt
    }

    fun compose(
        media: CropBox.Rect,
        crop: CropBox.Rect?,
        rotateChain: List<Int?>,
        userUnit: Double = 1.0
    ): Display {
        require(userUnit == 1.0) { "/UserUnit $userUnit is out of declared scope — refused, not scaled" }
        val effective = CropBox.effective(media, crop)
        val rotation = PdfRotate.resolve(rotateChain)
        val (w, h) = PdfRotate.visibleSize(effective.width, effective.height, rotation)
        return Display(effective, rotation, w, h)
    }

    /** Letter upright: the reference sanity case. */
    fun letterUpright(): Display = compose(CropBox.LETTER, null, listOf(null))
}
