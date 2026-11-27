// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Constant-time comparison of byte arrays. Compares every byte regardless of
 * early mismatch so that execution time does not depend on how many leading
 * bytes match — preventing timing-side-channel attacks on secret comparison
 * (e.g. HMAC tags, API keys, password hashes).
 *
 * Declared narrowings: arrays at most [MAX_LEN] bytes each; lengths are
 * compared in constant time as well (the result is XORed into the accumulator
 * so that length differences also contribute uniformly); the comparison is
 * over raw bytes — no encoding, canonicalisation, or trimming is applied.
 * Returns true only when both length and every byte are equal.
 */
object ConstantTimeCompare {
    const val MAX_LEN = 1 shl 16  // 64 KiB

    fun equals(a: ByteArray, b: ByteArray): Boolean {
        require(a.size <= MAX_LEN) { "array a exceeds $MAX_LEN bytes" }
        require(b.size <= MAX_LEN) { "array b exceeds $MAX_LEN bytes" }
        var result = a.size.xor(b.size).toInt()
        val n = if (a.size < b.size) a.size else b.size
        for (i in 0 until n) {
            result = result or ((a[i].toInt() xor b[i].toInt()) and 0xFF)
        }
        return result == 0
    }

    fun equalsConstantTime(a: ByteArray, b: ByteArray): Boolean = equals(a, b)
}
