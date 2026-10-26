package dev.fusion.hw

/**
 * Display geometry: diagonal inches from pixel dimensions and DPI
 * (diag_px / dpi), plus PPI from physical size — the two inverse
 * directions spec sheets quote, kept exact so the about-screen never
 * rounds twice.
 *
 * Declared narrowings: uniform square pixels assumed (xdpi==ydpi
 * inputs — anisotropic panels take the two-dpi overload REFUSING
 * silent averaging); zero/negative dimensions refuse.
 */
object DisplayInch {
    const val SCOPE = "Diagonal/PPI math, square pixels; anisotropic averaging refused, not hidden."

    fun diagonalInches(widthPx: Int, heightPx: Int, dpi: Double): Double {
        require(widthPx > 0 && heightPx > 0) { "degenerate panel" }
        require(dpi > 0 && dpi.isFinite()) { "dpi " + dpi }
        val diag = Math.sqrt(widthPx.toDouble() * widthPx + heightPx.toDouble() * heightPx)
        return diag / dpi
    }

    fun ppi(widthPx: Int, heightPx: Int, diagonalInches: Double): Double {
        require(widthPx > 0 && heightPx > 0) { "degenerate panel" }
        require(diagonalInches > 0 && diagonalInches.isFinite()) { "diagonal " + diagonalInches }
        return Math.sqrt(widthPx.toDouble() * widthPx + heightPx.toDouble() * heightPx) / diagonalInches
    }

    fun diagonalInches(widthPx: Int, heightPx: Int, xdpi: Double, ydpi: Double): Double {
        require(xdpi > 0 && ydpi > 0 && xdpi.isFinite() && ydpi.isFinite()) { "dpi pair" }
        require(kotlin.math.abs(xdpi - ydpi) / maxOf(xdpi, ydpi) < 0.01) {
            "anisotropic panel (" + xdpi + " vs " + ydpi + ") — refusing silent averaging; measure per axis"
        }
        return diagonalInches(widthPx, heightPx, (xdpi + ydpi) / 2.0)
    }
}
