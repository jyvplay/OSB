// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import kotlin.math.log2

/**
 * Shannon entropy (bits per symbol) over byte strings.
 *
 * Sits next to [XorSplit] (share math) and [TotpCode] (code math):
 * nothing measures how much uncertainty a secret actually carries. From
 * byte frequencies this computes −Σ p·log2(p): 0 for a single repeated
 * byte, 1 for a fair coin over two symbols, 8 for uniform random bytes.
 * [totalBits] scales by length for key-size reasoning. This is descriptive
 * information theory, not a strength verdict — "password1" scores nonzero
 * bits here while falling to any dictionary, so no threshold, grade, or
 * accept/reject is offered; policy kernels ([PinRules] lives nearby for
 * PINs) own verdicts. Empty input is refused (no symbols, no entropy).
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object ShannonBits {
    const val MAX_LEN = 1_048_576

    /**
     * Shannon entropy in bits per symbol over [data].
     *
     * @throws IllegalArgumentException on empty or over-cap input.
     */
    fun bitsPerSymbol(data: ByteArray): Double {
        require(data.size in 1..MAX_LEN) { "bad length" }
        val counts = IntArray(256)
        for (b in data) counts[b.toInt() and 0xFF]++
        var h = 0.0
        for (c in counts) {
            if (c > 0) {
                val p = c.toDouble() / data.size
                h -= p * log2(p)
            }
        }
        return h
    }

    /** Total entropy bits: per-symbol entropy times length. */
    fun totalBits(data: ByteArray): Double = bitsPerSymbol(data) * data.size
}
