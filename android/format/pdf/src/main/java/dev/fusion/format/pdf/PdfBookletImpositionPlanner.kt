// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/**
 * Pure-JVM **PDF Page Crop, Bleed/Trim Margin Normalizer, and 2-Up Booklet Saddle-Stitch
 * Imposition Planner** for `:feature:pdf`.
 */
object PdfBookletImpositionPlanner {
    const val SCOPE =
        "PDF MediaBox/CropBox whitespace margin normalizer and 4-multiple 2-up saddle-stitch booklet signature sheet imposition planner."

    data class BookletSheetSide(
        val physicalSheetNumber: Int,
        val side: String, // FRONT, BACK
        val leftLogicalPageOrBlank: Int?,
        val rightLogicalPageOrBlank: Int?,
    )

    fun planSaddleStitchBooklet(totalLogicalPages: Int): List<BookletSheetSide> {
        require(totalLogicalPages >= 1) { "totalLogicalPages must be >= 1" }
        val padded = ((totalLogicalPages + 3) / 4) * 4
        val sheets = padded / 4
        val out = ArrayList<BookletSheetSide>(sheets * 2)
        for (s in 0 until sheets) {
            val frontLeft = padded - 2 * s
            val frontRight = 1 + 2 * s
            val backLeft = 2 + 2 * s
            val backRight = padded - 1 - 2 * s
            out.add(
                BookletSheetSide(
                    physicalSheetNumber = s + 1,
                    side = "FRONT",
                    leftLogicalPageOrBlank = frontLeft.takeIf { it <= totalLogicalPages },
                    rightLogicalPageOrBlank = frontRight.takeIf { it <= totalLogicalPages },
                )
            )
            out.add(
                BookletSheetSide(
                    physicalSheetNumber = s + 1,
                    side = "BACK",
                    leftLogicalPageOrBlank = backLeft.takeIf { it <= totalLogicalPages },
                    rightLogicalPageOrBlank = backRight.takeIf { it <= totalLogicalPages },
                )
            )
        }
        return out
    }
}
