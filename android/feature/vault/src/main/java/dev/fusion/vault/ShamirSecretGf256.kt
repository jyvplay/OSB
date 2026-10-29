// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import java.security.SecureRandom

/**
 * Clean-room Shamir $(t, n)$ Threshold Secret Sharing over $\text{GF}(2^8)$ (`ShamirSecretGf256`).
 *
 * Splits an arbitrary-length vault master secret `S` into `n` distinct shares `(x_i, y_i)` (`x_i in 1..255`)
 * by evaluating a random degree-$(t - 1)$ polynomial $P_b(x) = S[b] \oplus a_1 x \oplus \dots \oplus a_{t-1} x^{t-1}$
 * independently per byte over $\text{GF}(2^8)$ (`0x11D`), and reconstructs `S` from ANY $t$ shares via
 * exact Lagrange basis evaluation at $x = 0$!
 *
 * Security property: any subset of $\le t - 1$ shares leaves every 256-value candidate for $S[b]$
 * strictly equiprobable (information-theoretic secrecy).
 */
object ShamirSecretGf256 {
    const val SCOPE =
        "Information-theoretic Shamir (t, n) threshold secret sharing over GF(2^8) (poly 0x11D, 2 <= t <= n <= 255, Lagrange x=0 reconstruction); Feldman verifiable commitments out of scope."

    private const val PRIM_POLY = 0x11D
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor PRIM_POLY
        }
    }

    data class Share(val x: Int, val y: ByteArray) {
        init {
            require(x in 1..255 && y.isNotEmpty()) { "Invalid Shamir share x=$x" }
        }
    }

    fun split(
        secret: ByteArray,
        threshold: Int,
        totalShares: Int,
        rng: SecureRandom = SecureRandom(),
    ): List<Share> {
        require(secret.isNotEmpty()) { "Secret must not be empty" }
        require(threshold >= 2 && totalShares in threshold..255) {
            "Invalid Shamir (t=$threshold, n=$totalShares) threshold geometry"
        }
        val outputs = Array(totalShares) { ByteArray(secret.size) }
        val coeffs = IntArray(threshold)
        val randBuf = ByteArray(threshold - 1)

        for (byteIdx in secret.indices) {
            coeffs[0] = secret[byteIdx].toInt() and 0xFF
            rng.nextBytes(randBuf)
            for (d in 1 until threshold) {
                coeffs[d] = randBuf[d - 1].toInt() and 0xFF
            }
            for (sIdx in 0 until totalShares) {
                val x = sIdx + 1
                // Horner's rule evaluation of P(x) over GF(256)
                var acc = 0
                for (d in (threshold - 1) downTo 0) {
                    acc = gfMul(acc, x) xor coeffs[d]
                }
                outputs[sIdx][byteIdx] = acc.toByte()
            }
        }
        coeffs.fill(0)
        randBuf.fill(0)
        return List(totalShares) { i -> Share(i + 1, outputs[i]) }
    }

    fun combine(shares: List<Share>): ByteArray {
        require(shares.size >= 2) { "At least 2 Shamir shares required" }
        val len = shares.first().y.size
        require(shares.all { it.y.size == len }) { "Mismatched Shamir share byte lengths" }
        val xs = shares.map { it.x }
        require(xs.toSet().size == xs.size) { "Duplicate Shamir x-coordinates refused" }

        // Precompute Lagrange basis weights L_j(0) = prod_{m != j} x_m / (x_m xor x_j)
        val lagrangeAtZero = IntArray(shares.size) { j ->
            var num = 1
            var den = 1
            val xj = shares[j].x
            for (m in shares.indices) {
                if (m != j) {
                    val xm = shares[m].x
                    num = gfMul(num, xm)
                    den = gfMul(den, xm xor xj)
                }
            }
            gfMul(num, gfInv(den))
        }

        val secret = ByteArray(len)
        for (byteIdx in 0 until len) {
            var acc = 0
            for (j in shares.indices) {
                acc = acc xor gfMul(shares[j].y[byteIdx].toInt() and 0xFF, lagrangeAtZero[j])
            }
            secret[byteIdx] = acc.toByte()
        }
        return secret
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a and 0xFF] + LOG[b and 0xFF]]
    }

    private fun gfInv(a: Int): Int = EXP[255 - LOG[a and 0xFF]]
}
