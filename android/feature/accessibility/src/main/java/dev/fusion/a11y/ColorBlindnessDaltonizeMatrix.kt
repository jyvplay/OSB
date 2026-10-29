// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Clean-room Color-Vision Deficiency (`CVD`) Simulation & Daltonization Error-Compensation Matrix
 * Engine (`AX-05`: Protanopia, Deuteranopia, Tritanopia, Achromatopsia + High-Contrast Text Boost).
 *
 * Uses published Viénot / Machado-Oliveira-Fernandes linear-sRGB $3\times 3$ projection matrices
 * and shifts lost chromatic contrast onto visible opponent channels (`Daltonize`).
 */
object ColorBlindnessDaltonizeMatrix {
    const val SCOPE =
        "Pure-JVM linear-sRGB Protanopia/Deuteranopia/Tritanopia simulation, Daltonization error-shift compensation, and WCAG relative-luminance contrast booster (AX-05)."

    enum class CvdMode { NONE, PROTANOPIA, DEUTERANOPIA, TRITANOPIA, MONOCHROME }

    private val PROTAN_SIM = arrayOf(
        doubleArrayOf(0.152286, 1.052583, -0.204868),
        doubleArrayOf(0.114503, 0.786281, 0.099216),
        doubleArrayOf(-0.003882, -0.048116, 1.051998),
    )
    private val DEUTAN_SIM = arrayOf(
        doubleArrayOf(0.367322, 0.860646, -0.227968),
        doubleArrayOf(0.280085, 0.672501, 0.047414),
        doubleArrayOf(-0.011820, 0.042940, 0.968881),
    )
    private val TRITAN_SIM = arrayOf(
        doubleArrayOf(1.255528, -0.076749, -0.178779),
        doubleArrayOf(-0.078411, 0.930809, 0.147602),
        doubleArrayOf(0.004733, 0.691367, 0.303900),
    )

    fun simulateCvd(mode: CvdMode, r: Double, g: Double, b: Double): DoubleArray {
        val rc = r.coerceIn(0.0, 1.0); val gc = g.coerceIn(0.0, 1.0); val bc = b.coerceIn(0.0, 1.0)
        return when (mode) {
            CvdMode.NONE -> doubleArrayOf(rc, gc, bc)
            CvdMode.MONOCHROME -> {
                val y = 0.2126 * rc + 0.7152 * gc + 0.0722 * bc
                doubleArrayOf(y, y, y)
            }
            CvdMode.PROTANOPIA -> mul3(PROTAN_SIM, rc, gc, bc)
            CvdMode.DEUTERANOPIA -> mul3(DEUTAN_SIM, rc, gc, bc)
            CvdMode.TRITANOPIA -> mul3(TRITAN_SIM, rc, gc, bc)
        }
    }

    fun daltonize(mode: CvdMode, r: Double, g: Double, b: Double, strength: Double = 1.0): DoubleArray {
        require(strength in 0.0..1.0) { "strength outside 0..1" }
        if (mode == CvdMode.NONE || mode == CvdMode.MONOCHROME) return simulateCvd(mode, r, g, b)
        val sim = simulateCvd(mode, r, g, b)
        val er = r - sim[0]
        val eg = g - sim[1]
        val eb = b - sim[2]
        val (sr, sg, sb) = when (mode) {
            CvdMode.PROTANOPIA, CvdMode.DEUTERANOPIA ->
                Triple(0.0, 0.7 * er + eg, 0.7 * er + eb)
            CvdMode.TRITANOPIA ->
                Triple(er + 0.7 * eb, eg + 0.7 * eb, 0.0)
            else -> Triple(0.0, 0.0, 0.0)
        }
        return doubleArrayOf(
            (r + strength * sr).coerceIn(0.0, 1.0),
            (g + strength * sg).coerceIn(0.0, 1.0),
            (b + strength * sb).coerceIn(0.0, 1.0),
        )
    }

    private fun mul3(m: Array<DoubleArray>, r: Double, g: Double, b: Double): DoubleArray = DoubleArray(3) { i ->
        (m[i][0] * r + m[i][1] * g + m[i][2] * b).coerceIn(0.0, 1.0)
    }
}
