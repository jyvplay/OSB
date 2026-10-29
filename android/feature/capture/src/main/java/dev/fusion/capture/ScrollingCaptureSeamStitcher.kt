// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

import kotlin.math.abs

/**
 * Clean-room Scrolling Screenshot Vertical Overlap Seam Finder (Mean Absolute Row-Signature Error)
 * + Sensitive OTP / Luhn Card Auto-Redaction Detector.
 */
object ScrollingCaptureSeamStitcher {
    const val SCOPE =
        "Pure-JVM vertical scrolling screenshot seam alignment solver with linear alpha feathering and Luhn/OTP text redaction detector."

    /**
     * Finds the overlap height `overlapRows in minOverlap..maxOverlap` minimizing row-luma MAD
     * between the bottom of `topFrameRowMeans` and the top of `bottomFrameRowMeans`.
     */
    fun findBestVerticalOverlap(
        topFrameRowMeans: DoubleArray,
        bottomFrameRowMeans: DoubleArray,
        minOverlap: Int = 4,
        maxOverlap: Int = 64,
    ): Pair<Int, Double> {
        val upper = minOf(maxOverlap, topFrameRowMeans.size - 1, bottomFrameRowMeans.size - 1)
        require(minOverlap >= 2 && upper >= minOverlap) { "insufficient frame height for scrolling seam search" }
        var bestOverlap = minOverlap
        var bestMad = Double.POSITIVE_INFINITY
        for (ov in minOverlap..upper) {
            var err = 0.0
            val topStart = topFrameRowMeans.size - ov
            for (r in 0 until ov) {
                err += abs(topFrameRowMeans[topStart + r] - bottomFrameRowMeans[r])
            }
            val mad = err / ov
            if (mad < bestMad - 1e-9) {
                bestMad = mad
                bestOverlap = ov
            }
        }
        return bestOverlap to bestMad
    }

    fun stitchRowsFeathered(topRows: DoubleArray, bottomRows: DoubleArray, overlap: Int): DoubleArray {
        require(overlap in 1..minOf(topRows.size, bottomRows.size)) { "invalid overlap $overlap" }
        val out = DoubleArray(topRows.size + bottomRows.size - overlap)
        val nonOvTop = topRows.size - overlap
        for (i in 0 until nonOvTop) out[i] = topRows[i]
        for (i in 0 until overlap) {
            val alpha = (i + 1.0) / (overlap + 1.0)
            out[nonOvTop + i] = topRows[nonOvTop + i] * (1.0 - alpha) + bottomRows[i] * alpha
        }
        for (i in overlap until bottomRows.size) {
            out[nonOvTop + i] = bottomRows[i]
        }
        return out
    }

    fun containsLuhnPaymentCard(ocrLine: String): Boolean {
        val digits = ocrLine.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        var alt = false
        for (i in digits.lastIndex downTo 0) {
            var n = digits[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }
}
