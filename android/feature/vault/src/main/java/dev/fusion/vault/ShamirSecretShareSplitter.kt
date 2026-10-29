// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import java.security.SecureRandom

/**
 * Clean-room `GF(2^8)` (`x^8 + x^4 + x^3 + x + 1` = `0x11B`) $(k, n)$ Shamir Secret Sharing
 * splitter & Lagrange polynomial interpolation reconstructor for Vault emergency recovery kits.
 */
object ShamirSecretShareSplitter {
    const val SCOPE =
        "Pure-JVM GF(256) (k, n) Shamir Secret Sharing threshold splitter and Lagrange interpolation secret reconstructor."

    data class Share(val index1To255: Int, val yBytes: ByteArray) {
        init {
            require(index1To255 in 1..255 && yBytes.isNotEmpty()) { "invalid share index/payload" }
        }
    }

    fun split(
        secret: ByteArray,
        thresholdK: Int,
        totalSharesN: Int,
        deterministicSeedForTestsOnly: ByteArray? = null,
    ): List<Share> {
        require(secret.size in 1..4096) { "secret size outside 1..4096" }
        require(thresholdK in 2..totalSharesN && totalSharesN <= 16) {
            "invalid threshold ($thresholdK, $totalSharesN)"
        }
        val sr = SecureRandom()
        var detState = deterministicSeedForTestsOnly?.fold(0x9E3779B97F4A7C15UL.toLong()) { a, b ->
            (a * 2862933555777941757L) + (b.toLong() and 0xFFL) + 3037000493L
        } ?: 0L

        fun nextRandomNonZeroByte(allowZero: Boolean): Int {
            while (true) {
                val v = if (deterministicSeedForTestsOnly != null) {
                    detState = detState * 6364136223846793005L + 1442695040888963407L
                    ((detState ushr 24) and 0xFFL).toInt()
                } else {
                    sr.nextInt(256)
                }
                if (allowZero || v != 0) return v
            }
        }

        val shareBuffers = Array(totalSharesN) { ByteArray(secret.size) }
        val coeffs = IntArray(thresholdK)
        for (byteIdx in secret.indices) {
            coeffs[0] = secret[byteIdx].toInt() and 0xFF
            for (c in 1 until thresholdK) {
                coeffs[c] = nextRandomNonZeroByte(allowZero = c < thresholdK - 1)
            }
            for (s in 0 until totalSharesN) {
                val x = s + 1
                var y = 0
                for (deg in thresholdK - 1 downTo 0) {
                    y = gfMul(y, x) xor coeffs[deg]
                }
                shareBuffers[s][byteIdx] = y.toByte()
            }
        }
        return List(totalSharesN) { i -> Share(i + 1, shareBuffers[i]) }
    }

    fun combine(shares: List<Share>): ByteArray {
        require(shares.size >= 2) { "at least 2 shares required" }
        val len = shares.first().yBytes.size
        require(shares.all { it.yBytes.size == len }) { "share byte-length mismatch" }
        require(shares.map { it.index1To255 }.distinct().size == shares.size) { "duplicate share x-coordinate" }

        val out = ByteArray(len)
        for (byteIdx in 0 until len) {
            var secretByte = 0
            for (i in shares.indices) {
                val xi = shares[i].index1To255
                val yi = shares[i].yBytes[byteIdx].toInt() and 0xFF
                var num = 1
                var den = 1
                for (j in shares.indices) {
                    if (i == j) continue
                    val xj = shares[j].index1To255
                    num = gfMul(num, xj) // evaluating at x = 0: (0 xor xj) = xj
                    den = gfMul(den, xi xor xj)
                }
                val basis = gfMul(num, gfInv(den))
                secretByte = secretByte xor gfMul(yi, basis)
            }
            out[byteIdx] = secretByte.toByte()
        }
        return out
    }

    private fun gfMul(a0: Int, b0: Int): Int {
        var a = a0 and 0xFF
        var b = b0 and 0xFF
        var p = 0
        repeat(8) {
            if ((b and 1) != 0) p = p xor a
            val hi = a and 0x80
            a = (a shl 1) and 0xFF
            if (hi != 0) a = a xor 0x1B
            b = b ushr 1
        }
        return p
    }

    private fun gfPow(a: Int, exp: Int): Int {
        var base = a and 0xFF
        var e = exp
        var res = 1
        while (e > 0) {
            if ((e and 1) != 0) res = gfMul(res, base)
            base = gfMul(base, base)
            e = e ushr 1
        }
        return res
    }

    private fun gfInv(a: Int): Int {
        require(a and 0xFF != 0) { "division by zero in GF(256)" }
        return gfPow(a, 254) // a^(2^8 - 2) = a^-1 in GF(2^8)*
    }
}
